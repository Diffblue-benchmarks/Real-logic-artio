/*
 * Copyright 2015-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.library;

import io.aeron.logbuffer.ControlledFragmentHandler.Action;
import org.agrona.DirectBuffer;
import uk.co.real_logic.fix_gateway.messages.DisconnectReason;
import uk.co.real_logic.fix_gateway.session.Session;

/**
 * Interface to implement to accept callbacks for FIX
 * session messages.
 */
public interface SessionHandler
{
    /**
     * Event to indicate that a fix message has arrived to process.
     *
     * @param buffer the buffer containing the fix message.
     * @param offset the offset in the buffer where the message starts.
     * @param length the length of the message within the buffer.
     * @param libraryId the id of library which has received this message.
     * @param session the session which has received this message.
     * @param messageType the FIX msgType field, encoded as an int.
     * @param timestampInNs the time of the message in nanoseconds.
     * @param position the position in the Aeron stream at the end of the message.
     * @return an action to indicate the correct back pressure behaviour.
     */
    Action onMessage(
        DirectBuffer buffer,
        int offset,
        int length,
        int libraryId,
        Session session,
        int sequenceIndex,
        int messageType,
        long timestampInNs,
        long position);

    /**
     * This session has timed out on this library. It is still connected, but will
     * be managed by the gateway.
     *  @param libraryId the id of library which the session used to owned by.
     * @param session the session that has timed out.
     */
    void onTimeout(int libraryId, Session session);

    /**
     * Invoked if a session has been detected as being, or no longer being demarcated as a slow
     * session.
     *  @param libraryId the id of library which the session used to owned by.
     * @param session the session that has become slow.
     * @param hasBecomeSlow true iff the session has been detected as slow, false if it is no longer slow.
     */
    void onSlowStatus(int libraryId, Session session, boolean hasBecomeSlow);

    /**
     * The session has disconnected.
     *
     * @param libraryId the id of library which the session used to owned by.
     * @param session the session that has disconnected.
     * @param reason the reason for the disconnection happening.
     * @return an action to indicate the correct back pressure behaviour.
     */
    Action onDisconnect(int libraryId, Session session, DisconnectReason reason);

    /**
     * Invoked When a client resets a session to the initial sequence number via a logon whilst still connected.
     * This is only called if there is actually a logon time available.
     *
     * @param session The session that has just started.
     */
    void onSessionStart(Session session);
}
