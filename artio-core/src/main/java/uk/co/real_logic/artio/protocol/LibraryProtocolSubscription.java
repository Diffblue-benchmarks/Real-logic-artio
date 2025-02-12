/*
 * Copyright 2015-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.protocol;

import io.aeron.logbuffer.ControlledFragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import uk.co.real_logic.artio.messages.*;

import static io.aeron.logbuffer.ControlledFragmentHandler.Action.ABORT;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.CONTINUE;

public final class LibraryProtocolSubscription implements ControlledFragmentHandler
{
    private final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
    private final ErrorDecoder error = new ErrorDecoder();
    private final ApplicationHeartbeatDecoder applicationHeartbeat = new ApplicationHeartbeatDecoder();
    private final ReleaseSessionReplyDecoder releaseSessionReply = new ReleaseSessionReplyDecoder();
    private final RequestSessionReplyDecoder requestSessionReply = new RequestSessionReplyDecoder();
    private final NewSentPositionDecoder newSentPosition = new NewSentPositionDecoder();
    private final ControlNotificationDecoder controlNotification = new ControlNotificationDecoder();
    private final SlowStatusNotificationDecoder slowStatusNotification = new SlowStatusNotificationDecoder();
    private final ResetLibrarySequenceNumberDecoder resetLibrarySequenceNumber =
        new ResetLibrarySequenceNumberDecoder();
    private final ManageSessionDecoder manageSession = new ManageSessionDecoder();
    private final FollowerSessionReplyDecoder followerSessionReply = new FollowerSessionReplyDecoder();
    private final EndOfDayDecoder endOfDay = new EndOfDayDecoder();

    private final LibraryEndPointHandler handler;

    public LibraryProtocolSubscription(final LibraryEndPointHandler handler)
    {
        this.handler = handler;
    }

    @SuppressWarnings("FinalParameters")
    public Action onFragment(final DirectBuffer buffer, int offset, final int length, final Header header)
    {
        messageHeader.wrap(buffer, offset);

        final int blockLength = messageHeader.blockLength();
        final int version = messageHeader.version();
        offset += messageHeader.encodedLength();

        switch (messageHeader.templateId())
        {
            case NewSentPositionDecoder.TEMPLATE_ID:
            {
                return onNewSentPosition(buffer, offset, blockLength, version);
            }

            case ManageSessionDecoder.TEMPLATE_ID:
            {
                return onManageSession(buffer, offset, blockLength, version);
            }

            case ErrorDecoder.TEMPLATE_ID:
            {
                return onError(buffer, offset, blockLength, version);
            }

            case ApplicationHeartbeatDecoder.TEMPLATE_ID:
            {
                return onApplicationHeartbeat(buffer, offset, blockLength, version);
            }

            case ReleaseSessionReplyDecoder.TEMPLATE_ID:
            {
                return onReleaseSessionReply(buffer, offset, blockLength, version);
            }

            case RequestSessionReplyDecoder.TEMPLATE_ID:
            {
                return onRequestSessionReply(buffer, offset, blockLength, version);
            }

            case ControlNotificationDecoder.TEMPLATE_ID:
            {
                return onControlNotification(buffer, offset, blockLength, version);
            }

            case SlowStatusNotificationDecoder.TEMPLATE_ID:
            {
                return onSlowStatusNotification(buffer, offset, blockLength, version);
            }

            case ResetLibrarySequenceNumberDecoder.TEMPLATE_ID:
            {
                return onResetLibrarySequenceNumber(buffer, offset, blockLength, version);
            }

            case FollowerSessionReplyDecoder.TEMPLATE_ID:
            {
                return onFollowerSessionReply(buffer, offset, blockLength, version);
            }

            case EndOfDayDecoder.TEMPLATE_ID:
            {
                return onEndOfDay(buffer, offset, blockLength, version);
            }
        }

        return CONTINUE;
    }

    private Action onControlNotification(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        controlNotification.wrap(buffer, offset, blockLength, version);
        final int libraryId = controlNotification.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }

        return handler.onControlNotification(
            libraryId,
            controlNotification.sessions());
    }

    private Action onSlowStatusNotification(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        slowStatusNotification.wrap(buffer, offset, blockLength, version);
        final int libraryId = slowStatusNotification.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }

        return handler.onSlowStatusNotification(
            libraryId,
            slowStatusNotification.connectionId(),
            slowStatusNotification.status() == SlowStatus.SLOW);
    }

    private Action onResetLibrarySequenceNumber(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        resetLibrarySequenceNumber.wrap(buffer, offset, blockLength, version);
        final int libraryId = resetLibrarySequenceNumber.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }

        return handler.onResetLibrarySequenceNumber(
            libraryId,
            resetLibrarySequenceNumber.session());
    }

    private Action onFollowerSessionReply(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        followerSessionReply.wrap(buffer, offset, blockLength, version);
        final int libraryId = followerSessionReply.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }

        return handler.onFollowerSessionReply(
            libraryId,
            followerSessionReply.replyToId(),
            followerSessionReply.session());
    }

    private Action onApplicationHeartbeat(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        applicationHeartbeat.wrap(buffer, offset, blockLength, version);
        return handler.onApplicationHeartbeat(applicationHeartbeat.libraryId());
    }

    private Action onReleaseSessionReply(
        final DirectBuffer buffer, final int offset, final int blockLength, final int version)
    {
        releaseSessionReply.wrap(buffer, offset, blockLength, version);
        final int libraryId = releaseSessionReply.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }

        return handler.onReleaseSessionReply(
            libraryId,
            releaseSessionReply.replyToId(),
            releaseSessionReply.status());
    }

    private Action onRequestSessionReply(
        final DirectBuffer buffer, final int offset, final int blockLength, final int version)
    {
        requestSessionReply.wrap(buffer, offset, blockLength, version);
        final int libraryId = requestSessionReply.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }

        return handler.onRequestSessionReply(
            libraryId,
            requestSessionReply.replyToId(),
            requestSessionReply.status());
    }

    private Action onError(
        final DirectBuffer buffer, final int offset, final int blockLength, final int version)
    {
        error.wrap(buffer, offset, blockLength, version);
        final int libraryId = error.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);
        if (action == ABORT)
        {
            return action;
        }
        return handler.onError(
            libraryId,
            error.errorType(),
            error.replyToId(),
            error.message());
    }

    private Action onNewSentPosition(
        final DirectBuffer buffer, final int offset, final int blockLength, final int version)
    {
        newSentPosition.wrap(buffer, offset, blockLength, version);
        // Deliberately don't keepalive the heartbeat - may not be a cluster leader

        return handler.onNewSentPosition(
            newSentPosition.libraryId(),
            newSentPosition.position());
    }

    private Action onManageSession(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        manageSession.wrap(buffer, offset, blockLength, version);
        final int libraryId = manageSession.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);

        if (ABORT == action)
        {
            return action;
        }

        return handler.onManageSession(
            libraryId,
            manageSession.connection(),
            manageSession.session(),
            manageSession.lastSentSequenceNumber(),
            manageSession.lastReceivedSequenceNumber(),
            manageSession.logonTime(),
            manageSession.sessionStatus(),
            manageSession.slowStatus(),
            manageSession.connectionType(),
            manageSession.sessionState(),
            manageSession.heartbeatIntervalInS(),
            manageSession.closedResendInterval() == Bool.TRUE,
            manageSession.resendRequestChunkSize(),
            manageSession.sendRedundantResendRequests() == Bool.TRUE,
            manageSession.enableLastMsgSeqNumProcessed() == Bool.TRUE,
            manageSession.replyToId(),
            manageSession.sequenceIndex(),
            manageSession.awaitingResend() == AwaitingResend.YES,
            manageSession.lastResentMsgSeqNo(),
            manageSession.lastResendChunkMsgSeqNum(),
            manageSession.endOfResendRequestRange(),
            manageSession.awaitingHeartbeat() == Bool.TRUE,
            manageSession.localCompId(),
            manageSession.localSubId(),
            manageSession.localLocationId(),
            manageSession.remoteCompId(),
            manageSession.remoteSubId(),
            manageSession.remoteLocationId(),
            manageSession.address(),
            manageSession.username(),
            manageSession.password());
    }

    private Action onEndOfDay(
        final DirectBuffer buffer,
        final int offset,
        final int blockLength,
        final int version)
    {
        endOfDay.wrap(buffer, offset, blockLength, version);
        final int libraryId = endOfDay.libraryId();
        final Action action = handler.onApplicationHeartbeat(libraryId);

        if (ABORT == action)
        {
            return action;
        }

        return handler.onEndOfDay(libraryId);
    }

}
