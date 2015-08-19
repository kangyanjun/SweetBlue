package com.idevicesinc.sweetblue;

import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import com.idevicesinc.sweetblue.BleServer.RequestListener;
import static com.idevicesinc.sweetblue.BleServer.RequestListener.*;
import static com.idevicesinc.sweetblue.BleServer.ResponseCompletionListener.*;
import com.idevicesinc.sweetblue.utils.UpdateLoop;
import com.idevicesinc.sweetblue.utils.Utils;

class P_BleServer_Listeners extends BluetoothGattServerCallback
{
	private final BleServer m_server;
	private final P_Logger m_logger;
	private final P_TaskQueue m_queue;

	final PA_Task.I_StateListener m_taskStateListener = new PA_Task.I_StateListener()
	{
		@Override public void onStateChange(PA_Task task, PE_TaskState state)
		{
			if ( task.getClass() == P_Task_DisconnectServer.class )
			{
				if (state == PE_TaskState.SUCCEEDED )
				{
					final P_Task_DisconnectServer task_cast = (P_Task_DisconnectServer) task;

					m_server.onNativeDisconnect(task_cast.m_nativeDevice.getAddress(), task_cast.isExplicit(), task_cast.getGattStatus());
				}
			}
			else
			{
				if( task.getClass() == P_Task_ConnectServer.class )
				{
					if( state == PE_TaskState.SUCCEEDED )
					{
						final P_Task_ConnectServer task_cast = (P_Task_ConnectServer) task;

						m_server.onNativeConnect(task_cast.m_nativeDevice.getAddress(), task_cast.isExplicit());
					}
				}
			}
		}
	};

	public P_BleServer_Listeners( BleServer server )
	{
		m_server = server;
		m_logger = m_server.getManager().getLogger();
		m_queue = m_server.getManager().getTaskQueue();
	}

	private boolean hasCurrentDisconnectTaskFor(final BluetoothDevice device)
	{
		final P_Task_DisconnectServer disconnectTask = m_queue.getCurrent(P_Task_DisconnectServer.class, m_server);

		return disconnectTask != null && disconnectTask.isFor(m_server, device.getAddress());
	}

	private boolean hasCurrentConnectTaskFor(final BluetoothDevice device)
	{
		final P_Task_ConnectServer connectTask = m_queue.getCurrent(P_Task_ConnectServer.class, m_server);

		return connectTask != null && connectTask.isFor(m_server, device.getAddress());
	}

	private void failDisconnectTaskIfPossibleFor(final BluetoothDevice device)
	{
		final P_Task_DisconnectServer disconnectTask = m_queue.getCurrent(P_Task_DisconnectServer.class, m_server);

		if( disconnectTask != null && disconnectTask.isFor(m_server, device.getAddress()) )
		{
			m_queue.fail(P_Task_DisconnectServer.class, m_server);
		}
	}

	private boolean failConnectTaskIfPossibleFor(final BluetoothDevice device, final int gattStatus)
	{
		if( hasCurrentConnectTaskFor(device) )
		{
			final P_Task_ConnectServer connectTask = m_queue.getCurrent(P_Task_ConnectServer.class, m_server);

			connectTask.onNativeFail(gattStatus);

			return true;
		}
		else
		{
			return false;
		}
	}

	private void onNativeConnectFail(final BluetoothDevice device, final int gattStatus)
	{
		//--- DRK > NOTE: Making an assumption that the underlying stack agrees that the connection state is STATE_DISCONNECTED.
		//---				This is backed up by basic testing, but even if the underlying stack uses a different value, it can probably
		//---				be assumed that it will eventually go to STATE_DISCONNECTED, so SweetBlue library logic is sounder "living under the lie" for a bit regardless.
		m_server.m_nativeWrapper.updateNativeConnectionState(device.getAddress(), BluetoothProfile.STATE_DISCONNECTED);

		if( hasCurrentConnectTaskFor(device) )
		{
			final P_Task_Connect connectTask = m_queue.getCurrent(P_Task_Connect.class, m_server);

			connectTask.onNativeFail(gattStatus);
		}
		else
		{
			m_server.onNativeConnectFail( device.getAddress(), gattStatus);
		}
	}

    @Override public void onConnectionStateChange(final BluetoothDevice device, final int gattStatus, final int newState)
	{
		final UpdateLoop updateLoop = m_server.getManager().getUpdateLoop();

		updateLoop.postIfNeeded(new Runnable()
		{
			@Override
			public void run()
			{
				m_logger.log_status(gattStatus, m_logger.gattConn(newState));

				if( newState == BluetoothProfile.STATE_DISCONNECTED )
				{
					m_server.m_nativeWrapper.updateNativeConnectionState(device.getAddress(), newState);

					final boolean wasConnecting = hasCurrentConnectTaskFor(device);

					if( !failConnectTaskIfPossibleFor(device, gattStatus) )
					{
						if( hasCurrentDisconnectTaskFor(device) )
						{
							final P_Task_DisconnectServer disconnectTask = m_queue.getCurrent(P_Task_DisconnectServer.class, m_server);

							disconnectTask.onNativeSuccess(gattStatus);
						}
						else
						{
							m_server.onNativeDisconnect(device.getAddress(), /*explicit=*/false, gattStatus);
						}
					}
				}
				else if( newState == BluetoothProfile.STATE_CONNECTING )
				{
					if( Utils.isSuccess(gattStatus) )
					{
						m_server.m_nativeWrapper.updateNativeConnectionState(device.getAddress(), newState);

//						m_device.onConnecting(/*definitelyExplicit=*/false, /*isReconnect=*/false, P_BondManager.OVERRIDE_EMPTY_STATES, /*bleConnect=*/true);

						failDisconnectTaskIfPossibleFor(device);

						if( !hasCurrentConnectTaskFor(device) )
						{
							final P_Task_ConnectServer task = new P_Task_ConnectServer(m_server, device, m_taskStateListener, /*explicit=*/false, PE_TaskPriority.FOR_IMPLICIT_BONDING_AND_CONNECTING);

							m_queue.add(task);
						}
					}
					else
					{
						onNativeConnectFail(device, gattStatus);
					}
				}
				else if( newState == BluetoothProfile.STATE_CONNECTED )
				{
					if( Utils.isSuccess(gattStatus) )
					{
						m_server.m_nativeWrapper.updateNativeConnectionState(device.getAddress(), newState);

						failDisconnectTaskIfPossibleFor(device);

						if( hasCurrentConnectTaskFor(device) )
						{
							m_queue.succeed(P_Task_ConnectServer.class, m_server);
						}
						else
						{
							m_server.onNativeConnect(device.getAddress(), /*explicit=*/false);
						}
					}
					else
					{
						onNativeConnectFail(device, gattStatus);
					}
				}
				//--- DRK > NOTE: never seen this case happen with BleDevice, we'll see if it happens with the server.
				else if( newState == BluetoothProfile.STATE_DISCONNECTING )
				{
					m_server.m_nativeWrapper.updateNativeConnectionState(device.getAddress(), newState);

					//--- DRK > error level just so it's noticeable...never seen this with client connections so we'll see if it hits with server ones.
					m_logger.e("Actually natively disconnecting server!");

					if( !hasCurrentDisconnectTaskFor(device) )
					{
						P_Task_DisconnectServer task = new P_Task_DisconnectServer(m_server, device, m_taskStateListener, /*explicit=*/false, PE_TaskPriority.FOR_IMPLICIT_BONDING_AND_CONNECTING);

						m_queue.add(task);
					}

					failConnectTaskIfPossibleFor(device, gattStatus);
				}
				else
				{
					m_server.m_nativeWrapper.updateNativeConnectionState(device);
				}
			}
		});
    }

	@Override public void onServiceAdded(int status, BluetoothGattService service)
	{
    }

	private BleServer.ResponseCompletionListener.ResponseCompletionEvent newEarlyOutResponse_Read(final BluetoothDevice device, final UUID charUuid, final UUID descUuid_nullable, final int requestId, final int offset, final BleServer.ResponseCompletionListener.Status status)
	{
		final Target target = descUuid_nullable == null ? Target.CHARACTERISTIC : Target.DESCRIPTOR;

		final ResponseCompletionEvent e = new ResponseCompletionEvent
		(
			m_server, device, charUuid, descUuid_nullable, Type.READ, target, BleServer.EMPTY_BYTE_ARRAY, BleServer.EMPTY_BYTE_ARRAY, requestId, offset, /*responseNeeded=*/true, status
		);

		return e;
	}

	private void onReadRequest(final BluetoothDevice device, final int requestId, final int offset, final UUID charUuid, final UUID descUuid_nullable)
	{
		final Target target = descUuid_nullable == null ? Target.CHARACTERISTIC : Target.DESCRIPTOR;

		final RequestListener listener = m_server.getListener_Request();

		if( listener == null )
		{
			m_server.invokeResponseListeners(newEarlyOutResponse_Read(device, charUuid, /*descUuid=*/null, requestId, offset, Status.NO_REQUEST_LISTENER_SET), null);
		}
		else
		{
			final RequestEvent requestEvent = new RequestEvent
			(
				m_server, device, charUuid, descUuid_nullable, Type.READ, target, BleServer.EMPTY_BYTE_ARRAY, requestId, offset, /*responseNeeded=*/true
			);

			final RequestListener.Please please = listener.onEvent(requestEvent);

			if( please == null)
			{
				m_server.invokeResponseListeners(newEarlyOutResponse_Read(device, charUuid, descUuid_nullable, requestId, offset, Status.NO_RESPONSE_ATTEMPTED), null);
			}
			else
			{
				final boolean attemptResponse = please.m_respond;

				if( attemptResponse )
				{
					final P_Task_SendReadWriteResponse responseTask = new P_Task_SendReadWriteResponse(m_server, requestEvent, please);
				}
				else
				{
					m_server.invokeResponseListeners(newEarlyOutResponse_Read(device, charUuid, descUuid_nullable, requestId, offset, Status.NO_RESPONSE_ATTEMPTED), please.m_responseListener);
				}
			}
		}
	}

	@Override public void onCharacteristicReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattCharacteristic characteristic)
	{
		final UpdateLoop updateLoop = m_server.getManager().getUpdateLoop();

		updateLoop.postIfNeeded(new Runnable()
		{
			@Override public void run()
			{
				onReadRequest(device, requestId, offset, characteristic.getUuid(), /*descUuid=*/null);
			}
		});
    }

	@Override public void onDescriptorReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattDescriptor descriptor)
	{
		final UpdateLoop updateLoop = m_server.getManager().getUpdateLoop();

		updateLoop.postIfNeeded(new Runnable()
		{
			@Override public void run()
			{
				onReadRequest(device, requestId, offset, descriptor.getCharacteristic().getUuid(), descriptor.getUuid());
			}
		});
	}

	private BleServer.ResponseCompletionListener.ResponseCompletionEvent newEarlyOutResponse_Write(final BluetoothDevice device, final Type type, final UUID charUuid, final UUID descUuid_nullable, final int requestId, final int offset, final BleServer.ResponseCompletionListener.Status status)
	{
		final Target target = descUuid_nullable == null ? Target.CHARACTERISTIC : Target.DESCRIPTOR;

		final ResponseCompletionEvent e = new ResponseCompletionEvent
		(
			m_server, device, charUuid, descUuid_nullable, type, target, BleServer.EMPTY_BYTE_ARRAY, BleServer.EMPTY_BYTE_ARRAY, requestId, offset, /*responseNeeded=*/true, status
		);

		return e;
	}


	private void onWriteRequest(final BluetoothDevice device, final int requestId, final int offset, final boolean preparedWrite, final boolean responseNeeded, final UUID charUuid, final UUID descUuid_nullable)
	{
		final Target target = descUuid_nullable == null ? Target.CHARACTERISTIC : Target.DESCRIPTOR;
		final Type type = preparedWrite ? Type.PREPARED_WRITE : Type.WRITE;

		final RequestListener listener = m_server.getListener_Request();

		if( listener == null )
		{
			m_server.invokeResponseListeners(newEarlyOutResponse_Write(device, type, charUuid, /*descUuid=*/null, requestId, offset, Status.NO_REQUEST_LISTENER_SET), null);
		}
		else
		{
			final RequestEvent requestEvent = new RequestEvent
			(
				m_server, device, charUuid, descUuid_nullable, type, target, BleServer.EMPTY_BYTE_ARRAY, requestId, offset, responseNeeded
			);

			final RequestListener.Please please = listener.onEvent(requestEvent);

			if( please == null)
			{
				m_server.invokeResponseListeners(newEarlyOutResponse_Write(device, type, charUuid, descUuid_nullable, requestId, offset, Status.NO_RESPONSE_ATTEMPTED), null);
			}
			else
			{
				final boolean attemptResponse = please.m_respond;

				if( attemptResponse )
				{
					final P_Task_SendReadWriteResponse responseTask = new P_Task_SendReadWriteResponse(m_server, requestEvent, please);
				}
				else
				{
					m_server.invokeResponseListeners(newEarlyOutResponse_Write(device, type, charUuid, descUuid_nullable, requestId, offset, Status.NO_RESPONSE_ATTEMPTED), please.m_responseListener);
				}
			}
		}
	}

	@Override public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value)
	{
		final UpdateLoop updateLoop = m_server.getManager().getUpdateLoop();

		updateLoop.postIfNeeded(new Runnable()
		{
			@Override public void run()
			{
				onWriteRequest(device, requestId, offset, preparedWrite, responseNeeded, characteristic.getUuid(), /*descUuid=*/null);
			}
		});
    }

	@Override public void onDescriptorWriteRequest( final BluetoothDevice device, final int requestId, final BluetoothGattDescriptor descriptor, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value)
	{
		final UpdateLoop updateLoop = m_server.getManager().getUpdateLoop();

		updateLoop.postIfNeeded(new Runnable()
		{
			@Override public void run()
			{
				onWriteRequest(device, requestId, offset, preparedWrite, responseNeeded, descriptor.getCharacteristic().getUuid(), descriptor.getUuid());
			}
		});
    }

	@Override public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute)
	{
    }

	@Override public void onNotificationSent( final BluetoothDevice device, final int status )
	{
		UpdateLoop updater = m_server.getManager().getUpdateLoop();
		
		updater.postIfNeeded(new Runnable()
		{
			@Override public void run()
			{
			}
		});
    }
}
