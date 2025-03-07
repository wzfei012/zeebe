// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/internal/utils"
	"github.com/camunda/zeebe/clients/go/pkg/pb"
	"github.com/golang/mock/gomock"
	"testing"
	"time"
)

func TestPublishMessageCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
	}
	stub := &pb.PublishMessageResponse{
		Key: 1,
	}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.MessageName("foo").CorrelationKey("bar").Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithMessageId(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		MessageId:      "hello",
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.MessageName("foo").CorrelationKey("bar").MessageId("hello").Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithTimeToLive(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		TimeToLive:     6 * 60 * 1000,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.MessageName("foo").CorrelationKey("bar").TimeToLive(6 * time.Minute).Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithVariablesFromString(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Variables:      variables,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.MessageName("foo").CorrelationKey("bar").VariablesFromString(variables)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithVariablesFromStringer(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Variables:      variables,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.MessageName("foo").CorrelationKey("bar").VariablesFromStringer(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithVariablesFromObject(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Variables:      variables,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.MessageName("foo").CorrelationKey("bar").VariablesFromObject(DataType{Foo: "bar"})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithVariablesFromObjectOmitempty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Variables:      variables,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.MessageName("foo").CorrelationKey("bar").VariablesFromObject(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithVariablesFromObjectIgnoreOmitEmpty(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"\"}"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Variables:      variables,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.MessageName("foo").CorrelationKey("bar").VariablesFromObjectIgnoreOmitempty(DataType{Foo: ""})
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestPublishMessageCommandWithVariablesFromMap(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	variables := "{\"foo\":\"bar\"}"
	variablesMap := make(map[string]interface{})
	variablesMap["foo"] = "bar"

	request := &pb.PublishMessageRequest{
		Name:           "foo",
		CorrelationKey: "bar",
		Variables:      variables,
	}
	stub := &pb.PublishMessageResponse{}

	client.EXPECT().PublishMessage(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewPublishMessageCommand(client, func(context.Context, error) bool { return false })

	variablesCommand, err := command.MessageName("foo").CorrelationKey("bar").VariablesFromMap(variablesMap)
	if err != nil {
		t.Error("Failed to set variables: ", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := variablesCommand.Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
