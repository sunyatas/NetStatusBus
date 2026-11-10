package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.ConnectivityManager;
import android.net.NetworkRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NetStatusBus#registerCallback}: a system refusal is logged instead of crashing init.
 */
public class CallbackRegistrationTest {

    private final List<String> messages = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();
    private ConnectivityManager manager;
    private ConnectivityManager.NetworkCallback callback;

    @Before
    public void setUp() {
        NetStatusReceiver.setErrorLoggerForTest(new NetStatusReceiver.ErrorLogger() {
            @Override
            public void log(String message, Throwable error) {
                messages.add(message);
                errors.add(error);
            }
        });
        manager = mock(ConnectivityManager.class);
        callback = mock(ConnectivityManager.NetworkCallback.class);
    }

    @After
    public void tearDown() {
        NetStatusReceiver.setErrorLoggerForTest(null);
    }

    @Test
    public void nullRequestRegistersDefaultNetworkCallback() {
        assertTrue(NetStatusBus.registerCallback(manager, callback, null));
        verify(manager).registerDefaultNetworkCallback(callback);
        verify(manager, never()).registerNetworkCallback(any(NetworkRequest.class),
                any(ConnectivityManager.NetworkCallback.class));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void requestRegistersCallbackForThatRequest() {
        NetworkRequest request = mock(NetworkRequest.class);
        assertTrue(NetStatusBus.registerCallback(manager, callback, request));
        verify(manager).registerNetworkCallback(request, callback);
        verify(manager, never()).registerDefaultNetworkCallback(any(ConnectivityManager.NetworkCallback.class));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void securityExceptionIsLoggedNotThrown() {
        SecurityException refusal = new SecurityException("Package android does not belong to 10123");
        doThrow(refusal).when(manager).registerDefaultNetworkCallback(callback);

        assertFalse(NetStatusBus.registerCallback(manager, callback, null));
        assertEquals(1, errors.size());
        assertSame(refusal, errors.get(0));
        assertTrue(messages.get(0).contains("init"));
    }

    @Test
    public void otherRuntimeExceptionIsLoggedNotThrown() {
        // Stands in for the hidden ConnectivityManager.TooManyRequestsException (a RuntimeException)
        RuntimeException tooMany = new RuntimeException("Too many NetworkRequests filed");
        NetworkRequest request = mock(NetworkRequest.class);
        doThrow(tooMany).when(manager).registerNetworkCallback(request, callback);

        assertFalse(NetStatusBus.registerCallback(manager, callback, request));
        assertEquals(1, errors.size());
        assertSame(tooMany, errors.get(0));
    }

    @Test
    public void errorsAreNotSwallowed() {
        OutOfMemoryError oom = new OutOfMemoryError();
        doThrow(oom).when(manager).registerDefaultNetworkCallback(callback);
        try {
            NetStatusBus.registerCallback(manager, callback, null);
            fail("Errors must propagate");
        } catch (OutOfMemoryError expected) {
            assertSame(oom, expected);
        }
        assertTrue(errors.isEmpty());
    }
}
