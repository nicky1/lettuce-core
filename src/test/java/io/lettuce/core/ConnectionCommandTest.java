/*
 * Copyright 2011-2018 the original author or authors.
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
package io.lettuce.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.code.tempusfugit.temporal.WaitFor;
import io.lettuce.Wait;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.test.util.ReflectionTestUtils;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * @author Will Glozer
 * @author Mark Paluch
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConnectionCommandTest extends AbstractRedisClientTest {
    @Test
    public void auth()  {
        new WithPasswordRequired() {
            @Override
            public void run(RedisClient client) {
                RedisCommands<String, String> connection = client.connect().sync();
                try {
                    connection.ping();
                    fail("Server doesn't require authentication");
                } catch (RedisException e) {
                    assertThat(e.getMessage()).isEqualTo("NOAUTH Authentication required.");
                    assertThat(connection.auth(passwd)).isEqualTo("OK");
                    assertThat(connection.set(key, value)).isEqualTo("OK");
                }

                RedisURI redisURI = RedisURI.Builder.redis(host, port).withDatabase(2).withPassword(passwd).build();
                RedisClient redisClient = DefaultRedisClient.get();
                RedisCommands<String, String> authConnection = redisClient.connect(redisURI).sync();
                authConnection.ping();
                authConnection.getStatefulConnection().close();
            }
        };
    }

    @Test
    public void echo()  {
        assertThat(redis.echo("hello")).isEqualTo("hello");
    }

    @Test
    public void ping()  {
        assertThat(redis.ping()).isEqualTo("PONG");
    }

    @Test
    public void select()  {
        redis.set(key, value);
        assertThat(redis.select(1)).isEqualTo("OK");
        assertThat(redis.get(key)).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void authNull()  {
        redis.auth(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void authEmpty()  {
        redis.auth("");
    }

    @Test
    public void authReconnect()  {
        new WithPasswordRequired() {
            @Override
            public void run(RedisClient client) throws InterruptedException {

                RedisCommands<String, String> connection = client.connect().sync();
                assertThat(connection.auth(passwd)).isEqualTo("OK");
                assertThat(connection.set(key, value)).isEqualTo("OK");
                connection.quit();

                Thread.sleep(100);
                assertThat(connection.get(key)).isEqualTo(value);
            }
        };
    }

    @Test
    public void selectReconnect()  {
        redis.select(1);
        redis.set(key, value);
        redis.quit();
        assertThat(redis.get(key)).isEqualTo(value);
    }

    @Test
    public void getSetReconnect(){
        redis.set(key, value);
        redis.quit();
        Wait.untilTrue(redis::isOpen).waitOrTimeout();
        assertThat(redis.get(key)).isEqualTo(value);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void authInvalidPassword()  {
        RedisAsyncCommands<String, String> async = client.connect().async();
        try {
            async.auth("invalid");
            fail("Authenticated with invalid password");
        } catch (RedisException e) {
            assertThat(e.getMessage()).isEqualTo("ERR Client sent AUTH, but no password is set");
            StatefulRedisConnection<String, String> statefulRedisCommands = async.getStatefulConnection();
            assertThat(ReflectionTestUtils.getField(statefulRedisCommands, "password")).isNull();
        } finally {
            async.getStatefulConnection().close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void selectInvalid()  {
        RedisAsyncCommands<String, String> async = client.connect().async();
        try {
            async.select(1024);
            fail("Selected invalid db index");
        } catch (RedisException e) {
            assertThat(e.getMessage()).startsWith("ERR");
            StatefulRedisConnection<String, String> statefulRedisCommands = async.getStatefulConnection();
            assertThat(ReflectionTestUtils.getField(statefulRedisCommands, "db")).isEqualTo(0);
        } finally {
            async.getStatefulConnection().close();
        }
    }

    @Test
    public void testDoubleToString()  {

        assertThat(LettuceStrings.string(1.1)).isEqualTo("1.1");
        assertThat(LettuceStrings.string(Double.POSITIVE_INFINITY)).isEqualTo("+inf");
        assertThat(LettuceStrings.string(Double.NEGATIVE_INFINITY)).isEqualTo("-inf");

    }
}
