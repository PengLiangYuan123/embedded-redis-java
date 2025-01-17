/*
 * Copyright 2024 shoothzj <shoothzj@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.embedded.redis.core;

import io.github.embedded.redis.core.util.CodecUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.ErrorRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.RedisMessage;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class RedisHandler extends ChannelInboundHandlerAdapter {

    private final RedisEngine redisEngine;

    public RedisHandler(RedisEngine redisEngine) {
        this.redisEngine = redisEngine;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ArrayRedisMessage arrayRedisMessage) {
            handleArrayCommand(ctx, arrayRedisMessage);
        } else {
            log.error("Unknown type message: {}", msg);
        }
    }

    private void handleArrayCommand(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        RedisMessage firstRedisMessage = arrayRedisMessage.children().get(0);
        if (firstRedisMessage instanceof FullBulkStringRedisMessage fullBulkStringRedisMessage) {
            String cmd = CodecUtil.str(fullBulkStringRedisMessage);
            try {
                CommandEnum commandEnum = CommandEnum.valueOf(cmd);
                switch (commandEnum) {
                    case HELLO -> handleHelloCmd(ctx, arrayRedisMessage);
                    case SET -> handleSetCmd(ctx, arrayRedisMessage);
                    case SETEX -> handleSetExCmd(ctx, arrayRedisMessage);
                    case GET -> handleGetCmd(ctx, arrayRedisMessage);
                    case DEL -> handleDelCmd(ctx, arrayRedisMessage);
                    case HSET -> handleHsetCmd(ctx, arrayRedisMessage);
                    case HGET -> handleHgetCmd(ctx, arrayRedisMessage);
                    case HMSET -> handleHmsetCmd(ctx, arrayRedisMessage);
                    case HMGET -> handleHmgetCmd(ctx, arrayRedisMessage);
                    case HDEL -> handleHdelCmd(ctx, arrayRedisMessage);
                    case HEXISTS -> handleHexistsCmd(ctx, arrayRedisMessage);
                    case HKEYS -> handleHkeysCmd(ctx, arrayRedisMessage);
                    case HVALS -> handleHvalsCmd(ctx, arrayRedisMessage);
                    case HGETALL -> handleHgetallCmd(ctx, arrayRedisMessage);
                    case LRANGE -> handleLrangeCmd(ctx, arrayRedisMessage);
                    case KEYS -> handleKeysCmd(ctx, arrayRedisMessage);
                    case PING -> handlePingCmd(ctx, arrayRedisMessage);
                    case FLUSHDB -> handleFlushDBCmd(ctx, arrayRedisMessage);
                    case FLUSHALL -> handleFlushAllCmd(ctx, arrayRedisMessage);
                }
            } catch (IllegalArgumentException e) {
                log.error("Unknown command: {}", cmd, e);
                ctx.writeAndFlush(new ErrorRedisMessage("ERR unknown command '" + cmd + "'"));
            }
        }
    }

    private void handleHelloCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        SimpleStringRedisMessage server = new SimpleStringRedisMessage("server");
        List<RedisMessage> list = new ArrayList<>();
        list.add(server);
        ctx.writeAndFlush(new ArrayRedisMessage(list));
    }

    private void handleSetCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        FullBulkStringRedisMessage valueMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(2);
        String key = CodecUtil.str(keyMsg);
        byte[] value = CodecUtil.bytes(valueMsg);
        redisEngine.set(key, value);
        ctx.writeAndFlush(new SimpleStringRedisMessage("OK"));
    }

    private void handleSetExCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        FullBulkStringRedisMessage expireMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(2);
        FullBulkStringRedisMessage valueMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(3);
        String key = CodecUtil.str(keyMsg);
        byte[] value = CodecUtil.bytes(valueMsg);
        int expire = Integer.parseInt(CodecUtil.str(expireMsg));
        redisEngine.setEx(key, value, expire);
        ctx.writeAndFlush(new SimpleStringRedisMessage("OK"));
    }

    private void handleGetCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        byte[] msg = redisEngine.getContent(key);
        if (msg == null) {
            ctx.writeAndFlush(FullBulkStringRedisMessage.NULL_INSTANCE);
        } else {
            ctx.writeAndFlush(new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(msg)));
        }
    }

    private void handleDelCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        List<RedisMessage> bulkStringMsgList = arrayRedisMessage.children()
                .subList(1, arrayRedisMessage.children().size());
        List<String> keys = bulkStringMsgList.stream()
                .map(redisMessage -> (FullBulkStringRedisMessage) redisMessage)
                .map(CodecUtil::str)
                .toList();
        long count = redisEngine.delete(keys);
        ctx.writeAndFlush(new IntegerRedisMessage(count));
    }

    private void handleHsetCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        FullBulkStringRedisMessage fieldMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(2);
        FullBulkStringRedisMessage valueMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(3);
        String key = CodecUtil.str(keyMsg);
        String field = CodecUtil.str(fieldMsg);
        byte[] value = CodecUtil.bytes(valueMsg);
        redisEngine.hset(key, field, value);
        ctx.writeAndFlush(new IntegerRedisMessage(1));
    }

    private void handleHgetCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        FullBulkStringRedisMessage fieldMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(2);
        String key = CodecUtil.str(keyMsg);
        String field = CodecUtil.str(fieldMsg);
        byte[] value = redisEngine.hget(key, field);
        if (value == null) {
            ctx.writeAndFlush(FullBulkStringRedisMessage.NULL_INSTANCE);
        } else {
            ctx.writeAndFlush(new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(value)));
        }
    }

    private void handleHmsetCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        Map<String, byte[]> hash = new HashMap<>();
        for (int i = 2; i < arrayRedisMessage.children().size(); i += 2) {
            FullBulkStringRedisMessage fieldMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(i);
            FullBulkStringRedisMessage valueMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(i + 1);
            String field = CodecUtil.str(fieldMsg);
            byte[] value = CodecUtil.bytes(valueMsg);
            hash.put(field, value);
        }
        redisEngine.hmset(key, hash);
        ctx.writeAndFlush(new SimpleStringRedisMessage("OK"));
    }

    private void handleHmgetCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        List<String> fields = arrayRedisMessage.children().stream()
                .skip(2)
                .map(redisMessage -> (FullBulkStringRedisMessage) redisMessage)
                .map(CodecUtil::str)
                .collect(Collectors.toList());
        List<byte[]> values = redisEngine.hmget(key, fields);
        List<RedisMessage> valueMessages = values.stream()
                .map(value -> value == null ? FullBulkStringRedisMessage.NULL_INSTANCE :
                        new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(value)))
                .collect(Collectors.toList());
        ctx.writeAndFlush(new ArrayRedisMessage(valueMessages));
    }

    private void handleHdelCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        List<String> fields = arrayRedisMessage.children().stream()
                .skip(2)
                .map(redisMessage -> (FullBulkStringRedisMessage) redisMessage)
                .map(CodecUtil::str)
                .collect(Collectors.toList());
        long deleted = redisEngine.hdel(key, fields);
        ctx.writeAndFlush(new IntegerRedisMessage(deleted));
    }

    private void handleHexistsCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        FullBulkStringRedisMessage fieldMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(2);
        String key = CodecUtil.str(keyMsg);
        String field = CodecUtil.str(fieldMsg);
        boolean exists = redisEngine.hexists(key, field);
        ctx.writeAndFlush(new IntegerRedisMessage(exists ? 1 : 0));
    }

    private void handleHkeysCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        Set<String> fields = redisEngine.hkeys(key);
        List<RedisMessage> fieldMessages = fields.stream()
                .map(field -> new FullBulkStringRedisMessage(Unpooled
                        .wrappedBuffer(field.getBytes(StandardCharsets.UTF_8))))
                .collect(Collectors.toList());
        ctx.writeAndFlush(new ArrayRedisMessage(fieldMessages));
    }

    private void handleHvalsCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        List<byte[]> values = redisEngine.hvals(key);
        List<RedisMessage> valueMessages = values.stream()
                .map(value -> new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(value)))
                .collect(Collectors.toList());
        ctx.writeAndFlush(new ArrayRedisMessage(valueMessages));
    }

    private void handleHgetallCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String key = CodecUtil.str(keyMsg);
        Map<String, byte[]> hash = redisEngine.hgetall(key);
        List<RedisMessage> hashMessages = hash.entrySet().stream()
                .flatMap(entry -> Stream.of(
                        new FullBulkStringRedisMessage(Unpooled
                                .wrappedBuffer(entry.getKey().getBytes(StandardCharsets.UTF_8))),
                        new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(entry.getValue()))
                ))
                .collect(Collectors.toList());
        ctx.writeAndFlush(new ArrayRedisMessage(hashMessages));
    }

    private void handleLrangeCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage keyMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        FullBulkStringRedisMessage startMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(2);
        FullBulkStringRedisMessage stopMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(3);
        String key = CodecUtil.str(keyMsg);
        int start = Integer.parseInt(CodecUtil.str(startMsg));
        int stop = Integer.parseInt(CodecUtil.str(stopMsg));

        List<byte[]> range = redisEngine.lrange(key, start, stop);
        List<RedisMessage> redisMessages = range.stream()
                .map(value -> new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(value)))
                .collect(Collectors.toList());
        ctx.writeAndFlush(new ArrayRedisMessage(redisMessages));
    }

    private void handleKeysCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        FullBulkStringRedisMessage patternMsg = (FullBulkStringRedisMessage) arrayRedisMessage.children().get(1);
        String pattern = CodecUtil.str(patternMsg);
        List<String> keys = redisEngine.keys(pattern);
        List<RedisMessage> redisMessages = keys.stream()
                .map(key -> (RedisMessage)
                        new FullBulkStringRedisMessage(Unpooled.wrappedBuffer(key.getBytes(StandardCharsets.UTF_8))))
                .toList();
        ctx.writeAndFlush(new ArrayRedisMessage(redisMessages));
    }

    private void handlePingCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        ctx.writeAndFlush(new FullBulkStringRedisMessage(
                Unpooled.wrappedBuffer("PONG".getBytes(StandardCharsets.UTF_8))));
    }

    private void handleFlushDBCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        redisEngine.flush();
        ctx.writeAndFlush(new SimpleStringRedisMessage("OK"));
    }

    private void handleFlushAllCmd(ChannelHandlerContext ctx, ArrayRedisMessage arrayRedisMessage) {
        redisEngine.flush();
        ctx.writeAndFlush(new SimpleStringRedisMessage("OK"));
    }

}
