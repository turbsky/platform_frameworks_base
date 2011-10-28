/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.database;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

/**
 * Native implementation of the bulk cursor. This is only for use in implementing
 * IPC, application code should use the Cursor interface.
 *
 * {@hide}
 */
public abstract class BulkCursorNative extends Binder implements IBulkCursor
{
    public BulkCursorNative()
    {
        attachInterface(this, descriptor);
    }

    /**
     * Cast a Binder object into a content resolver interface, generating
     * a proxy if needed.
     */
    static public IBulkCursor asInterface(IBinder obj)
    {
        if (obj == null) {
            return null;
        }
        IBulkCursor in = (IBulkCursor)obj.queryLocalInterface(descriptor);
        if (in != null) {
            return in;
        }

        return new BulkCursorProxy(obj);
    }
    
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            switch (code) {
                case GET_CURSOR_WINDOW_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    int startPos = data.readInt();
                    CursorWindow window = getWindow(startPos);
                    if (window == null) {
                        reply.writeInt(0);
                        return true;
                    }
                    reply.writeNoException();
                    reply.writeInt(1);
                    window.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    return true;
                }

                case COUNT_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    int count = count();
                    reply.writeNoException();
                    reply.writeInt(count);
                    return true;
                }

                case GET_COLUMN_NAMES_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    String[] columnNames = getColumnNames();
                    reply.writeNoException();
                    reply.writeInt(columnNames.length);
                    int length = columnNames.length;
                    for (int i = 0; i < length; i++) {
                        reply.writeString(columnNames[i]);
                    }
                    return true;
                }

                case DEACTIVATE_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    deactivate();
                    reply.writeNoException();
                    return true;
                }
                
                case CLOSE_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    close();
                    reply.writeNoException();
                    return true;
                }

                case REQUERY_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    IContentObserver observer =
                        IContentObserver.Stub.asInterface(data.readStrongBinder());
                    CursorWindow window = CursorWindow.CREATOR.createFromParcel(data);
                    int count = requery(observer, window);
                    reply.writeNoException();
                    reply.writeInt(count);
                    reply.writeBundle(getExtras());
                    return true;
                }

                case ON_MOVE_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    int position = data.readInt();
                    onMove(position);
                    reply.writeNoException();
                    return true;
                }

                case WANTS_ON_MOVE_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    boolean result = getWantsAllOnMoveCalls();
                    reply.writeNoException();
                    reply.writeInt(result ? 1 : 0);
                    return true;
                }

                case GET_EXTRAS_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    Bundle extras = getExtras();
                    reply.writeNoException();
                    reply.writeBundle(extras);
                    return true;
                }

                case RESPOND_TRANSACTION: {
                    data.enforceInterface(IBulkCursor.descriptor);
                    Bundle extras = data.readBundle();
                    Bundle returnExtras = respond(extras);
                    reply.writeNoException();
                    reply.writeBundle(returnExtras);
                    return true;
                }
            }
        } catch (Exception e) {
            DatabaseUtils.writeExceptionToParcel(reply, e);
            return true;
        }

        return super.onTransact(code, data, reply, flags);
    }

    public IBinder asBinder()
    {
        return this;
    }
}


final class BulkCursorProxy implements IBulkCursor {
    private IBinder mRemote;
    private Bundle mExtras;

    public BulkCursorProxy(IBinder remote)
    {
        mRemote = remote;
        mExtras = null;
    }

    public IBinder asBinder()
    {
        return mRemote;
    }

    public CursorWindow getWindow(int startPos) throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);
            data.writeInt(startPos);

            mRemote.transact(GET_CURSOR_WINDOW_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);

            CursorWindow window = null;
            if (reply.readInt() == 1) {
                window = CursorWindow.newFromParcel(reply);
            }
            return window;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public void onMove(int position) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);
            data.writeInt(position);

            mRemote.transact(ON_MOVE_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public int count() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);

            boolean result = mRemote.transact(COUNT_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);

            int count;
            if (result == false) {
                count = -1;
            } else {
                count = reply.readInt();
            }
            return count;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public String[] getColumnNames() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);

            mRemote.transact(GET_COLUMN_NAMES_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);

            String[] columnNames = null;
            int numColumns = reply.readInt();
            columnNames = new String[numColumns];
            for (int i = 0; i < numColumns; i++) {
                columnNames[i] = reply.readString();
            }
            return columnNames;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public void deactivate() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);

            mRemote.transact(DEACTIVATE_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public void close() throws RemoteException
    {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);

            mRemote.transact(CLOSE_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
    
    public int requery(IContentObserver observer, CursorWindow window) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);
            data.writeStrongInterface(observer);
            window.writeToParcel(data, 0);

            boolean result = mRemote.transact(REQUERY_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);

            int count;
            if (!result) {
                count = -1;
            } else {
                count = reply.readInt();
                mExtras = reply.readBundle();
            }
            return count;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public boolean getWantsAllOnMoveCalls() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);

            mRemote.transact(WANTS_ON_MOVE_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);

            int result = reply.readInt();
            return result != 0;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public Bundle getExtras() throws RemoteException {
        if (mExtras == null) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(IBulkCursor.descriptor);

                mRemote.transact(GET_EXTRAS_TRANSACTION, data, reply, 0);
                DatabaseUtils.readExceptionFromParcel(reply);

                mExtras = reply.readBundle();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
        return mExtras;
    }

    public Bundle respond(Bundle extras) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IBulkCursor.descriptor);
            data.writeBundle(extras);

            mRemote.transact(RESPOND_TRANSACTION, data, reply, 0);
            DatabaseUtils.readExceptionFromParcel(reply);

            Bundle returnExtras = reply.readBundle();
            return returnExtras;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}

