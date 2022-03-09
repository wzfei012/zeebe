/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class DbMessageSubscriptionState
    implements MutableMessageSubscriptionState,
        MutablePendingMessageSubscriptionState,
        StreamProcessorLifecycleAware {

  // (elementInstanceKey, messageName) => MessageSubscription
  private final DbForeignKey<DbLong> elementInstanceKey;
  private final DbString messageName;
  private final MessageSubscription messageSubscription;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbString> elementKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbString>, MessageSubscription>
      subscriptionColumnFamily;

  // (messageName, correlationKey, elementInstanceKey) => \0
  private final DbString correlationKey;
  private final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>
      nameCorrelationAndElementInstanceKey;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>, DbNil>
      messageNameAndCorrelationKeyColumnFamily;

  private final PendingMessageSubscriptionState transientState =
      new PendingMessageSubscriptionState(this);

  public DbMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    elementInstanceKey = DbElementInstanceState.foreignKey();
    messageName = new DbString();
    messageSubscription = new MessageSubscription();
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
            transactionContext,
            elementKeyAndMessageName,
            messageSubscription);

    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationAndElementInstanceKey =
        new DbCompositeKey<>(nameAndCorrelationKey, elementInstanceKey);
    messageNameAndCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
            transactionContext,
            nameCorrelationAndElementInstanceKey,
            DbNil.INSTANCE);
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    subscriptionColumnFamily.forEach(
        subscription -> {
          if (subscription.isCorrelating()) {
            transientState.add(subscription.getRecord());
          }
        });
  }

  @Override
  public MessageSubscription get(final long elementInstanceKey, final DirectBuffer messageName) {
    this.messageName.wrapBuffer(messageName);
    this.elementInstanceKey.inner().wrapLong(elementInstanceKey);
    return subscriptionColumnFamily.get(elementKeyAndMessageName);
  }

  @Override
  public void visitSubscriptions(
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final MessageSubscriptionVisitor visitor) {

    this.messageName.wrapBuffer(messageName);
    this.correlationKey.wrapBuffer(correlationKey);

    messageNameAndCorrelationKeyColumnFamily.whileEqualPrefix(
        nameAndCorrelationKey,
        (compositeKey, nil) -> {
          return visitMessageSubscription(elementKeyAndMessageName, visitor);
        });
  }

  @Override
  public boolean existSubscriptionForElementInstance(
      final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.inner().wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);

    return subscriptionColumnFamily.exists(elementKeyAndMessageName);
  }

  @Override
  public void put(final long key, final MessageSubscriptionRecord record) {
    elementInstanceKey.inner().wrapLong(record.getElementInstanceKey());
    messageName.wrapBuffer(record.getMessageNameBuffer());

    messageSubscription.setKey(key).setRecord(record).setCorrelating(false);

    subscriptionColumnFamily.put(elementKeyAndMessageName, messageSubscription);

    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    messageNameAndCorrelationKeyColumnFamily.put(
        nameCorrelationAndElementInstanceKey, DbNil.INSTANCE);
  }

  @Override
  public void updateToCorrelatingState(final MessageSubscriptionRecord record) {
    final var messageKey = record.getMessageKey();
    var messageVariables = record.getVariablesBuffer();
    if (record == messageSubscription.getRecord()) {
      // copy the buffer before loading the subscription to avoid that it is overridden
      messageVariables = BufferUtil.cloneBuffer(record.getVariablesBuffer());
    }

    final var subscription = get(record.getElementInstanceKey(), record.getMessageNameBuffer());
    if (subscription == null) {
      throw new IllegalStateException(
          String.format(
              "Expected subscription but not found. [element-instance-key: %d, message-name: %s]",
              record.getElementInstanceKey(), record.getMessageName()));
    }

    // update the message key and the variables
    subscription.getRecord().setMessageKey(messageKey).setVariables(messageVariables);

    updateCorrelatingFlag(subscription, true);

    transientState.add(record);
  }

  @Override
  public void updateToCorrelatedState(final MessageSubscription subscription) {
    updateCorrelatingFlag(subscription, false);
    transientState.remove(subscription.getRecord());
  }

  @Override
  public boolean remove(final long elementInstanceKey, final DirectBuffer messageName) {
    this.elementInstanceKey.inner().wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);

    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyAndMessageName);

    final boolean found = messageSubscription != null;
    if (found) {
      remove(messageSubscription);
    }
    return found;
  }

  @Override
  public void remove(final MessageSubscription subscription) {
    subscriptionColumnFamily.delete(elementKeyAndMessageName);

    final var record = subscription.getRecord();
    messageName.wrapBuffer(record.getMessageNameBuffer());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    messageNameAndCorrelationKeyColumnFamily.delete(nameCorrelationAndElementInstanceKey);

    transientState.remove(subscription.getRecord());
  }

  private void updateCorrelatingFlag(
      final MessageSubscription subscription, final boolean correlating) {
    final var record = subscription.getRecord();
    elementInstanceKey.inner().wrapLong(record.getElementInstanceKey());
    messageName.wrapBuffer(record.getMessageNameBuffer());

    subscription.setCorrelating(correlating);
    subscriptionColumnFamily.put(elementKeyAndMessageName, subscription);
  }

  private Boolean visitMessageSubscription(
      final DbCompositeKey<DbForeignKey<DbLong>, DbString> elementKeyAndMessageName,
      final MessageSubscriptionVisitor visitor) {
    final MessageSubscription messageSubscription =
        subscriptionColumnFamily.get(elementKeyAndMessageName);

    if (messageSubscription == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find subscription with key %d and %s, but no subscription found",
              elementKeyAndMessageName.first().inner().getValue(),
              elementKeyAndMessageName.second()));
    }
    return visitor.visit(messageSubscription);
  }

  @Override
  public void visitSubscriptionBefore(
      final long deadline, final MessageSubscriptionVisitor visitor) {
    transientState.visitSubscriptionBefore(deadline, visitor);
  }

  @Override
  public void updateCommandSentTime(final MessageSubscriptionRecord record, final long sentTime) {
    transientState.updateCommandSentTime(record, sentTime);
  }
}
