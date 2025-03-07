/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.util.sched.ActorControl;
import org.agrona.collections.Int2IntHashMap;

public final class TopologyPartitionListenerImpl implements TopologyPartitionListener {

  private final Int2IntHashMap partitionLeaders = new Int2IntHashMap(-1);
  private final ActorControl actor;

  public TopologyPartitionListenerImpl(final ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void onPartitionLeaderUpdated(final int partitionId, final BrokerInfo member) {
    if (member.getPartitionRoles().get(partitionId) == PartitionRole.LEADER) {
      actor.submit(() -> updatePartitionLeader(partitionId, member));
    }
  }

  private void updatePartitionLeader(final int partitionId, final BrokerInfo member) {
    final int currentLeader = partitionLeaders.get(partitionId);

    if (currentLeader != member.getNodeId()) {
      partitionLeaders.put(partitionId, member.getNodeId());
    }
  }

  public Int2IntHashMap getPartitionLeaders() {
    return partitionLeaders;
  }
}
