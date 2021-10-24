/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.camunda.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import org.agrona.DirectBuffer;

public final class DbMessageState implements MutableMessageState {

  /**
   * <pre>message key -> message
   */
  private final ColumnFamily<DbLong, StoredMessage> messageColumnFamily;

  /**
   * <pre>name | correlation key | key -> []
   *
   * find message by name and correlation key - the message key ensures the queue ordering
   */
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, DbNil>
      nameCorrelationMessageColumnFamily;

  /**
   * <pre>deadline | key -> []
   *
   * find messages which are before a given timestamp
   */
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> deadlineColumnFamily;

  /**
   * <pre>name | correlation key | message id -> []
   *
   * exist a message for a given message name, correlation key and message id
   */
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, DbNil>
      messageIdColumnFamily;

  /**
   * <pre>key | bpmn process id -> []
   *
   * check if a message is correlated to a process
   */
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil> correlatedMessageColumnFamily;

  /**
   * <pre> bpmn process id | correlation key -> []
   *
   * check if a process instance is created by this correlation key
   */
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, DbNil>
      activeProcessInstancesByCorrelationKeyColumnFamiliy;

  /**
   * <pre> process instance key -> correlation key
   *
   * get correlation key by process instance key
   */
  private final ColumnFamily<DbLong, DbString> processInstanceCorrelationKeyColumnFamiliy;

  public DbMessageState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    final DbLong messageKey;
    final StoredMessage message;

    messageKey = new DbLong();
    message = new StoredMessage();
    messageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_KEY, transactionContext, messageKey, message);

    final DbString messageName;

    final DbString correlationKey;
    final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong> nameCorrelationMessageKey;
    final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;

    messageName = new DbString();
    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationMessageKey = new DbCompositeKey<>(nameAndCorrelationKey, messageKey);
    nameCorrelationMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGES,
            transactionContext,
            nameCorrelationMessageKey,
            DbNil.INSTANCE);

    final DbLong deadline;

    final DbCompositeKey<DbLong, DbLong> deadlineMessageKey;

    deadline = new DbLong();
    deadlineMessageKey = new DbCompositeKey<>(deadline, messageKey);
    deadlineColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_DEADLINES,
            transactionContext,
            deadlineMessageKey,
            DbNil.INSTANCE);

    final DbString messageId;

    final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString> nameCorrelationMessageIdKey;

    messageId = new DbString();
    nameCorrelationMessageIdKey = new DbCompositeKey<>(nameAndCorrelationKey, messageId);
    messageIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_IDS,
            transactionContext,
            nameCorrelationMessageIdKey,
            DbNil.INSTANCE);

    final DbCompositeKey<DbLong, DbString> messageBpmnProcessIdKey;

    final DbString bpmnProcessIdKey;

    bpmnProcessIdKey = new DbString();
    messageBpmnProcessIdKey = new DbCompositeKey<>(messageKey, bpmnProcessIdKey);
    correlatedMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_CORRELATED,
            transactionContext,
            messageBpmnProcessIdKey,
            DbNil.INSTANCE);

    final DbCompositeKey<DbString, DbString> bpmnProcessIdCorrelationKey;

    bpmnProcessIdCorrelationKey = new DbCompositeKey<>(bpmnProcessIdKey, correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamiliy =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
            transactionContext,
            bpmnProcessIdCorrelationKey,
            DbNil.INSTANCE);

    final DbLong processInstanceKey;
    processInstanceKey = new DbLong();
    processInstanceCorrelationKeyColumnFamiliy =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,
            transactionContext,
            processInstanceKey,
            correlationKey);
  }

  @Override
  public void put(final long key, final MessageRecord record) {
    final DbLong messageKey;
    final StoredMessage message;

    messageKey = new DbLong();
    message = new StoredMessage();

    messageKey.wrapLong(key);
    message.setMessageKey(key).setMessage(record);
    messageColumnFamily.put(messageKey, message);

    final DbString messageName;

    final DbString correlationKey;
    final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong> nameCorrelationMessageKey;
    final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;

    messageName = new DbString();
    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationMessageKey = new DbCompositeKey<>(nameAndCorrelationKey, messageKey);

    messageName.wrapBuffer(record.getNameBuffer());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    nameCorrelationMessageColumnFamily.put(nameCorrelationMessageKey, DbNil.INSTANCE);

    final DbLong deadline;

    final DbCompositeKey<DbLong, DbLong> deadlineMessageKey;

    deadline = new DbLong();
    deadlineMessageKey = new DbCompositeKey<>(deadline, messageKey);

    deadline.wrapLong(record.getDeadline());
    deadlineColumnFamily.put(deadlineMessageKey, DbNil.INSTANCE);

    final DbString messageId;

    final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString> nameCorrelationMessageIdKey;

    messageId = new DbString();
    nameCorrelationMessageIdKey = new DbCompositeKey<>(nameAndCorrelationKey, messageId);

    final DirectBuffer recordMessageIdBuffer = record.getMessageIdBuffer();
    if (recordMessageIdBuffer.capacity() > 0) {
      messageId.wrapBuffer(recordMessageIdBuffer);
      messageIdColumnFamily.put(nameCorrelationMessageIdKey, DbNil.INSTANCE);
    }
  }

  @Override
  public void putMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    correlatedMessageColumnFamily.put(
        buildKeyCorrelatedMessage(messageKey, bpmnProcessId), DbNil.INSTANCE);
  }

  @Override
  public void removeMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    correlatedMessageColumnFamily.delete(buildKeyCorrelatedMessage(messageKey, bpmnProcessId));
  }

  @Override
  public void putActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    activeProcessInstancesByCorrelationKeyColumnFamiliy.put(
        buildKeyProcessInstanceCorrelation(bpmnProcessId, correlationKey), DbNil.INSTANCE);
  }

  @Override
  public void removeActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    activeProcessInstancesByCorrelationKeyColumnFamiliy.delete(
        buildKeyProcessInstanceCorrelation(bpmnProcessId, correlationKey));
  }

  @Override
  public void putProcessInstanceCorrelationKey(
      final long processInstanceKey, final DirectBuffer correlationKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    this.processInstanceKey.wrapLong(processInstanceKey);
    this.correlationKey.wrapBuffer(correlationKey);
    processInstanceCorrelationKeyColumnFamiliy.put(this.processInstanceKey, this.correlationKey);
  }

  @Override
  public void removeProcessInstanceCorrelationKey(final long processInstanceKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);

    this.processInstanceKey.wrapLong(processInstanceKey);
    processInstanceCorrelationKeyColumnFamiliy.delete(this.processInstanceKey);
  }

  @Override
  public void remove(final long key) {
    final StoredMessage storedMessage = getMessage(key);
    if (storedMessage == null) {
      return;
    }

    messageKey.wrapLong(storedMessage.getMessageKey());
    messageColumnFamily.delete(messageKey);

    messageName.wrapBuffer(storedMessage.getMessage().getNameBuffer());
    correlationKey.wrapBuffer(storedMessage.getMessage().getCorrelationKeyBuffer());

    nameCorrelationMessageColumnFamily.delete(nameCorrelationMessageKey);

    final DirectBuffer messageId = storedMessage.getMessage().getMessageIdBuffer();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.delete(nameCorrelationMessageIdKey);
    }

    deadline.wrapLong(storedMessage.getMessage().getDeadline());
    deadlineColumnFamily.delete(deadlineMessageKey);

    correlatedMessageColumnFamily.whileEqualPrefix(
        messageKey,
        ((compositeKey, zbNil) -> {
          correlatedMessageColumnFamily.delete(compositeKey);
        }));
  }

  DbCompositeKey<DbString, DbString> buildKeyProcessInstanceCorrelation(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {

    final DbString bpmnProcessIdKeyPart = new DbString();
    bpmnProcessIdKeyPart.wrapBuffer(bpmnProcessId);
    final DbString correlationKeyPart = new DbString();
    correlationKeyPart.wrapBuffer(correlationKey);

    return new DbCompositeKey<>(bpmnProcessIdKeyPart, correlationKeyPart);
  }

  private DbCompositeKey<DbLong, DbString> buildKeyCorrelatedMessage(
      final long messageKeyArg, final DirectBuffer bpmnProcessId) {
    final DbString bpmnProcessIdKey = new DbString();
    final DbLong messageKey = new DbLong();

    messageKey.wrapLong(messageKeyArg);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);

    return new DbCompositeKey<>(messageKey, bpmnProcessIdKey);
  }

  @Override
  public boolean existMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    return correlatedMessageColumnFamily.exists(
        buildKeyCorrelatedMessage(messageKey, bpmnProcessId));
  }

  @Override
  public boolean existActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    return activeProcessInstancesByCorrelationKeyColumnFamiliy.exists(
        buildKeyProcessInstanceCorrelation(bpmnProcessId, correlationKey));
  }

  @Override
  public DirectBuffer getProcessInstanceCorrelationKey(final long processInstanceKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);

    this.processInstanceKey.wrapLong(processInstanceKey);
    final var correlationKey =
        processInstanceCorrelationKeyColumnFamiliy.get(this.processInstanceKey);

    return correlationKey != null ? correlationKey.getBuffer() : null;
  }

  @Override
  public void visitMessages(
      final DirectBuffer name, final DirectBuffer correlationKey, final MessageVisitor visitor) {

    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);

    nameCorrelationMessageColumnFamily.whileEqualPrefix(
        nameAndCorrelationKey,
        (compositeKey, nil) -> {
          final long messageKey = compositeKey.getSecond().getValue();
          final StoredMessage message = getMessage(messageKey);
          return visitor.visit(message);
        });
  }

  @Override
  public StoredMessage getMessage(final long messageKey) {
    this.messageKey.wrapLong(messageKey);
    return messageColumnFamily.get(this.messageKey);
  }

  @Override
  public void visitMessagesWithDeadlineBefore(final long timestamp, final MessageVisitor visitor) {
    deadlineColumnFamily.whileTrue(
        ((compositeKey, zbNil) -> {
          final long deadline = compositeKey.getFirst().getValue();
          if (deadline <= timestamp) {
            final long messageKey = compositeKey.getSecond().getValue();
            final StoredMessage message = getMessage(messageKey);
            return visitor.visit(message);
          }
          return false;
        }));
  }

  @Override
  public boolean exist(
      final DirectBuffer name, final DirectBuffer correlationKey, final DirectBuffer messageId) {
    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);
    this.messageId.wrapBuffer(messageId);

    return messageIdColumnFamily.exists(nameCorrelationMessageIdKey);
  }
}
