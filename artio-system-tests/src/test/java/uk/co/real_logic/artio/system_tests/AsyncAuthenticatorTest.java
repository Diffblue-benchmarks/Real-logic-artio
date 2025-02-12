/*
 * Copyright 2015-2019 Real Logic Ltd, Adaptive Financial Consulting Ltd.
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
package uk.co.real_logic.artio.system_tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.decoder.LogonDecoder;
import uk.co.real_logic.artio.engine.EngineConfiguration;
import uk.co.real_logic.artio.engine.FixEngine;
import uk.co.real_logic.artio.library.LibraryConfiguration;
import uk.co.real_logic.artio.session.Session;
import uk.co.real_logic.artio.validation.AuthenticationProxy;
import uk.co.real_logic.artio.validation.AuthenticationStrategy;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static uk.co.real_logic.artio.TestFixtures.launchMediaDriver;
import static uk.co.real_logic.artio.Timing.assertEventuallyTrue;
import static uk.co.real_logic.artio.system_tests.SystemTestUtil.*;

public class AsyncAuthenticatorTest extends AbstractGatewayToGatewaySystemTest
{
    private static final int DEFAULT_TIMEOUT_IN_MS = 1_000;
    private final FakeConnectHandler fakeConnectHandler = new FakeConnectHandler();
    private final FakeAsyncAuthenticationStrategy auth = new FakeAsyncAuthenticationStrategy();

    @Before
    public void launch()
    {
        delete(ACCEPTOR_LOGS);

        mediaDriver = launchMediaDriver();

        final EngineConfiguration acceptingConfig = acceptingConfig(port, ACCEPTOR_ID, INITIATOR_ID);
        acceptingConfig.authenticationStrategy(auth);

        acceptingEngine = FixEngine.launch(acceptingConfig);
        initiatingEngine = launchInitiatingEngine(libraryAeronPort);

        final LibraryConfiguration acceptingLibraryConfig = acceptingLibraryConfig(acceptingHandler);
        acceptingLibraryConfig.libraryConnectHandler(fakeConnectHandler);
        acceptingLibrary = connect(acceptingLibraryConfig);
        initiatingLibrary = newInitiatingLibrary(libraryAeronPort, initiatingHandler);
        testSystem = new TestSystem(acceptingLibrary, initiatingLibrary);
    }

    @Test
    public void messagesCanBeSentFromInitiatorToAcceptor()
    {
        final Reply<Session> reply = initiate(initiatingLibrary, port, INITIATOR_ID, ACCEPTOR_ID);

        acquireAuthProxy(reply);

        auth.accept();
        completeConnectSessions(reply);
        messagesCanBeExchanged();
        assertInitiatingSequenceIndexIs(0);
    }

    @Test
    public void messagesCanBeSentFromInitiatorToAcceptorAfterFailedAuthenticationAttempt()
    {
        final Reply<Session> invalidReply = initiate(initiatingLibrary, port, INITIATOR_ID, ACCEPTOR_ID);

        acquireAuthProxy(invalidReply);

        auth.reject();

        completeFailedSession(invalidReply);

        auth.reset();

        final Reply<Session> validReply = initiate(initiatingLibrary, port, INITIATOR_ID, ACCEPTOR_ID);

        acquireAuthProxy(validReply);

        auth.accept();
        completeConnectSessions(validReply);
        messagesCanBeExchanged();
        assertInitiatingSequenceIndexIs(1);
    }

    @After
    public void teardown()
    {
        auth.verifyNoBlockingCalls();
    }

    private void acquireAuthProxy(final Reply<Session> reply)
    {
        assertEventuallyTrue("failed to receive auth proxy", () ->
        {
            testSystem.poll();
            return auth.hasAuthenticateBeenInvoked();
        }, DEFAULT_TIMEOUT_IN_MS);

        assertEquals(Reply.State.EXECUTING, reply.state());
    }

    private class FakeAsyncAuthenticationStrategy implements AuthenticationStrategy
    {
        volatile boolean blockingAuthenticateCalled;
        private volatile AuthenticationProxy authProxy;

        public void authenticateAsync(final LogonDecoder logon, final AuthenticationProxy authProxy)
        {
            this.authProxy = authProxy;

            assertThat(authProxy.remoteAddress(), containsString("127.0.0.1"));
        }

        public boolean authenticate(final LogonDecoder logon)
        {
            blockingAuthenticateCalled = true;

            throw new UnsupportedOperationException();
        }

        void verifyNoBlockingCalls()
        {
            assertFalse(blockingAuthenticateCalled);
        }

        void accept()
        {
            authProxy.accept();
        }

        void reject()
        {
            authProxy.reject();
        }

        boolean hasAuthenticateBeenInvoked()
        {
            return authProxy != null;
        }

        void reset()
        {
            authProxy = null;
        }
    }
}
