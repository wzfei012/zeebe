/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.Either;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;

/**
 * Checks the preconditions of a state transition command.
 *
 * <p>A process instance can be have concurrent state transitions if a user command is received
 * (e.g. cancel process instance) or if an internal/external event is triggered (e.g. timer boundary
 * event). In this case, the current process instance processing needs to be interrupted be avoid an
 * inconsistent state.
 */
public final class ProcessInstanceStateTransitionGuard {

  private final BpmnStateBehavior stateBehavior;

  public ProcessInstanceStateTransitionGuard(final BpmnStateBehavior stateBehavior) {
    this.stateBehavior = stateBehavior;
  }

  /**
   * Checks if the preconditions of the given command are met.
   *
   * @return {@code true} if the preconditions are met and the transition command is valid.
   */
  public Either<Failure, ?> isValidStateTransition(final BpmnElementContext context) {
    return checkStateTransition(context).mapLeft(Failure::new);
  }

  private Either<String, ?> checkStateTransition(final BpmnElementContext context) {
    // migrated processors expect their state to be set via event appliers, and non migrated
    // processors in the processor. this means that ELEMENT_COMPLETING of a migrated processor will
    // set the element state to ELEMENT_COMPLETING on replay, but ELEMENT_COMPLETING of a non
    // migrated will expect its state to ALREADY be ELEMENT_COMPLETING. To avoid too much
    // complexity, when reprocessing events for non migrated processors, just allow allow the
    // transition as well; this could in the future be a source of error, but as its only
    // temporary until all processors are migrated, it's OK for now
    // TODO(npepinpe): remove as part of https://github.com/camunda-cloud/zeebe/issues/6202
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      if (context.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETING) {
        return hasElementInstanceWithState(
                context, context.getIntent(), ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .flatMap(ok -> hasActiveFlowScopeInstance(context));
      } else if (context.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {
        return hasElementInstanceWithState(
            context,
            context.getIntent(),
            ProcessInstanceIntent.ELEMENT_COMPLETING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
      } else if (context.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED) {
        return hasElementInstanceWithState(
            context, context.getIntent(), ProcessInstanceIntent.ELEMENT_TERMINATING);
      }
    }

    switch (context.getIntent()) {
      case COMPLETE_ELEMENT:
        // an incident is resolved by writing a COMPLETE command when the element instance is in
        // state COMPLETING
        return hasElementInstanceWithState(
                context,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                ProcessInstanceIntent.ELEMENT_COMPLETING)
            .flatMap(ok -> hasActiveFlowScopeInstance(context));
      case TERMINATE_ELEMENT:
        return hasElementInstanceWithState(
            context,
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING);

      case ELEMENT_ACTIVATING:
      case ELEMENT_ACTIVATED:
      case ELEMENT_COMPLETING:
      case ELEMENT_COMPLETED:
        return hasElementInstanceWithState(context, context.getIntent())
            .flatMap(ok -> hasActiveFlowScopeInstance(context));

      case ELEMENT_TERMINATING:
      case ELEMENT_TERMINATED:
        return hasElementInstanceWithState(context, context.getIntent());

      case ACTIVATE_ELEMENT:
      case SEQUENCE_FLOW_TAKEN:
        return hasActiveFlowScopeInstance(context);

      default:
        return Either.left(
            String.format(
                "Expected event to have a process instance intent but was '%s'",
                context.getIntent()));
    }
  }

  private Either<String, ElementInstance> getElementInstance(final BpmnElementContext context) {
    final var elementInstance = stateBehavior.getElementInstance(context);
    if (elementInstance != null) {
      return Either.right(elementInstance);

    } else {
      return Either.left(
          String.format(
              "Expected element instance with key '%d' to be present in state but not found.",
              context.getElementInstanceKey()));
    }
  }

  private Either<String, ElementInstance> getFlowScopeInstance(final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);
    if (flowScopeInstance != null) {
      return Either.right(flowScopeInstance);

    } else {
      return Either.left(
          String.format(
              "Expected flow scope instance with key '%d' to be present in state but not found.",
              context.getFlowScopeKey()));
    }
  }

  private Either<String, ElementInstance> hasElementInstanceInState(
      final ElementInstance elementInstance,
      final ProcessInstanceIntent expectedState,
      final ProcessInstanceIntent... otherExpected) {
    final var currentState = elementInstance.getState();
    if (expectedState != currentState && !Arrays.asList(otherExpected).contains(currentState)) {
      return Either.left(
          String.format(
              "Expected element instance to be in state '%s' or one of '%s' but was '%s'.",
              expectedState, Arrays.toString(otherExpected), currentState));
    } else {
      return Either.right(elementInstance);
    }
  }

  private Either<String, ?> hasElementInstanceWithState(
      final BpmnElementContext context,
      final ProcessInstanceIntent expectedState,
      final ProcessInstanceIntent... otherExpected) {
    // a shortcut to improve readability
    return getElementInstance(context)
        .flatMap(
            elementInstance ->
                hasElementInstanceInState(elementInstance, expectedState, otherExpected));
  }

  private Either<String, ElementInstance> hasFlowScopeInstanceInState(
      final ElementInstance flowScopeInstance, final ProcessInstanceIntent expectedState) {
    final var currentState = flowScopeInstance.getState();
    if (currentState != expectedState) {
      return Either.left(
          String.format(
              "Expected flow scope instance to be in state '%s' but was '%s'.",
              expectedState, currentState));

    } else {
      return Either.right(flowScopeInstance);
    }
  }

  private Either<String, ElementInstance> hasNonInterruptedFlowScope(
      final ElementInstance flowScopeInstance, final BpmnElementContext context) {
    final var interruptingElementId = flowScopeInstance.getInterruptingElementId();
    if (flowScopeInstance.isInterrupted()
        && !interruptingElementId.equals(context.getElementId())) {
      return Either.left(
          String.format(
              "Expected flow scope instance to be not interrupted but was interrupted by an event with id '%s'.",
              BufferUtil.bufferAsString(interruptingElementId)));

    } else {
      return Either.right(flowScopeInstance);
    }
  }

  private Either<String, ?> hasActiveFlowScopeInstance(final BpmnElementContext context) {
    // a shortcut to improve readability
    if (context.getBpmnElementType() == BpmnElementType.PROCESS) {
      // a process has no flow scope instance
      return Either.right(null);

    } else {
      return getFlowScopeInstance(context)
          .flatMap(
              flowScopeInstance ->
                  hasFlowScopeInstanceInState(
                      flowScopeInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED))
          .flatMap(flowScopeInstance -> hasNonInterruptedFlowScope(flowScopeInstance, context));
    }
  }

  public void registerStateTransition(
      final BpmnElementContext context, final ProcessInstanceIntent newState) {
    switch (newState) {
      case ELEMENT_ACTIVATING:
      case ELEMENT_ACTIVATED:
      case ELEMENT_COMPLETING:
      case ELEMENT_COMPLETED:
      case ELEMENT_TERMINATING:
      case ELEMENT_TERMINATED:
        updateElementInstanceState(context, newState);
        break;

      default:
        // other transitions doesn't change the state of an element instance
        break;
    }
  }

  private void updateElementInstanceState(
      final BpmnElementContext context, final ProcessInstanceIntent newState) {

    stateBehavior.updateElementInstance(
        context, elementInstance -> elementInstance.setState(newState));
  }
}
