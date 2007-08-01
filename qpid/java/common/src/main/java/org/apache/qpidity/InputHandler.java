/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpidity;

import java.nio.ByteBuffer;

import static org.apache.qpidity.InputHandler.State.*;


/**
 * InputHandler
 *
 * @author Rafael H. Schloming
 */

class InputHandler implements Handler<ByteBuffer>
{

    enum State
    {
        PROTO_HDR,
        PROTO_HDR_M,
        PROTO_HDR_Q,
        PROTO_HDR_P,
        PROTO_HDR_CLASS,
        PROTO_HDR_INSTANCE,
        PROTO_HDR_MAJOR,
        PROTO_HDR_MINOR,
        FRAME_HDR,
        FRAME_HDR_TYPE,
        FRAME_HDR_SIZE1,
        FRAME_HDR_SIZE2,
        FRAME_HDR_RSVD1,
        FRAME_HDR_TRACK,
        FRAME_HDR_CH1,
        FRAME_HDR_CH2,
        FRAME_HDR_RSVD2,
        FRAME_HDR_RSVD3,
        FRAME_HDR_RSVD4,
        FRAME_HDR_RSVD5,
        FRAME_PAYLOAD,
        FRAME_FRAGMENT,
        ERROR;
    }

    private final ProtocolActions actions;
    private State state;

    private byte instance;
    private byte major;
    private byte minor;

    private byte flags;
    private byte type;
    private byte track;
    private int channel;
    private int size;
    private Frame frame;

    public InputHandler(ProtocolActions actions, State state)
    {
        this.actions = actions;
        this.state = state;
    }

    public InputHandler(ProtocolActions actions)
    {
        this(actions, PROTO_HDR);
    }

    private void init()
    {
        actions.init(new ProtocolHeader(instance, major, minor));
    }

    private void frame()
    {
        assert size == frame.getSize();
        actions.frame(frame);
        frame = null;
    }

    private void error(String fmt, Object ... args)
    {
        actions.error(new ProtocolError(fmt, args));
    }

    public void handle(ByteBuffer buf)
    {
        while (buf.hasRemaining())
        {
            state = next(buf);
        }
    }

    private State next(ByteBuffer buf)
    {
        switch (state) {
        case PROTO_HDR:
            return expect(buf, 'A', PROTO_HDR_M);
        case PROTO_HDR_M:
            return expect(buf, 'M', PROTO_HDR_Q);
        case PROTO_HDR_Q:
            return expect(buf, 'Q', PROTO_HDR_P);
        case PROTO_HDR_P:
            return expect(buf, 'P', PROTO_HDR_CLASS);
        case PROTO_HDR_CLASS:
            return expect(buf, 1, PROTO_HDR_INSTANCE);
        case PROTO_HDR_INSTANCE:
            instance = buf.get();
            return PROTO_HDR_MAJOR;
        case PROTO_HDR_MAJOR:
            major = buf.get();
            return PROTO_HDR_MINOR;
        case PROTO_HDR_MINOR:
            minor = buf.get();
            init();
            return FRAME_HDR;
        case FRAME_HDR:
            flags = buf.get();
            return FRAME_HDR_TYPE;
        case FRAME_HDR_TYPE:
            type = buf.get();
            return FRAME_HDR_SIZE1;
        case FRAME_HDR_SIZE1:
            size = buf.get() << 8;
            return FRAME_HDR_SIZE2;
        case FRAME_HDR_SIZE2:
            size += buf.get();
            size -= 12;
            return FRAME_HDR_RSVD1;
        case FRAME_HDR_RSVD1:
            return expect(buf, 0, FRAME_HDR_TRACK);
        case FRAME_HDR_TRACK:
            byte b = buf.get();
            if ((b & 0xF0) != 0) {
                error("non-zero reserved bits in upper nibble of " +
                      "frame header byte 5: '%x'", b);
                return ERROR;
            } else {
                track = (byte) (b & 0xF);
                return FRAME_HDR_CH1;
            }
        case FRAME_HDR_CH1:
            channel = buf.get() << 8;
            return FRAME_HDR_CH2;
        case FRAME_HDR_CH2:
            channel += buf.get();
            return FRAME_HDR_RSVD2;
        case FRAME_HDR_RSVD2:
            return expect(buf, 0, FRAME_HDR_RSVD3);
        case FRAME_HDR_RSVD3:
            return expect(buf, 0, FRAME_HDR_RSVD4);
        case FRAME_HDR_RSVD4:
            return expect(buf, 0, FRAME_HDR_RSVD5);
        case FRAME_HDR_RSVD5:
            return expect(buf, 0, FRAME_PAYLOAD);
        case FRAME_PAYLOAD:
            frame = new Frame(flags, type, track, channel);
            if (size > buf.remaining()) {
                frame.addFragment(buf.slice());
                buf.position(buf.limit());
                return FRAME_FRAGMENT;
            } else {
                ByteBuffer payload = buf.slice();
                payload.limit(size);
                buf.position(buf.position() + size);
                frame.addFragment(payload);
                frame();
                return FRAME_HDR;
            }
        case FRAME_FRAGMENT:
            int delta = size - frame.getSize();
            if (delta > buf.remaining()) {
                frame.addFragment(buf.slice());
                buf.position(buf.limit());
                return FRAME_FRAGMENT;
            } else {
                ByteBuffer fragment = buf.slice();
                fragment.limit(delta);
                buf.position(buf.position() + delta);
                frame.addFragment(fragment);
                frame();
                return FRAME_HDR;
            }
        default:
            throw new IllegalStateException();
        }
    }

    private State expect(ByteBuffer buf, int expected, State next)
    {
        return expect(buf, (byte) expected, next);
    }

    private State expect(ByteBuffer buf, char expected, State next)
    {
        return expect(buf, (byte) expected, next);
    }

    private State expect(ByteBuffer buf, byte expected, State next)
    {
        byte b = buf.get();
        if (b == expected) {
            return next;
        } else {
            error("expecting '%x', got '%x'", expected, b);
            return ERROR;
        }
    }

}
