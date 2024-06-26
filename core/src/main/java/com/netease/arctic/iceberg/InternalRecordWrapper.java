/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netease.arctic.iceberg;

import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.function.Function;

/**
 * Copy from {@link org.apache.iceberg.data.InternalRecordWrapper}.
 * Add new constructor to create InternalRecordWrapper cheap
 */
public class InternalRecordWrapper implements StructLike {
  private final Function<Object, Object>[] transforms;
  private StructLike wrapped = null;

  @SuppressWarnings("unchecked")
  public InternalRecordWrapper(Types.StructType struct) {
    this.transforms = struct.fields().stream()
        .map(field -> converter(field.type()))
        .toArray(length -> (Function<Object, Object>[]) Array.newInstance(Function.class, length));
  }

  public InternalRecordWrapper(Function<Object, Object>[] transforms) {
    this.transforms = transforms;
  }

  public static Function<Object, Object> converter(Type type) {
    switch (type.typeId()) {
      case DATE:
        return date -> DateTimeUtil.daysFromDate((LocalDate) date);
      case TIME:
        return time -> DateTimeUtil.microsFromTime((LocalTime) time);
      case TIMESTAMP:
        if (((Types.TimestampType) type).shouldAdjustToUTC()) {
          return timestamp -> DateTimeUtil.microsFromTimestamptz((OffsetDateTime) timestamp);
        } else {
          return timestamp -> DateTimeUtil.microsFromTimestamp((LocalDateTime) timestamp);
        }
      case FIXED:
        return bytes -> ByteBuffer.wrap((byte[]) bytes);
      case STRUCT:
        InternalRecordWrapper wrapper = new InternalRecordWrapper(type.asStructType());
        return struct -> wrapper.wrap((StructLike) struct);
      default:
    }
    return null;
  }

  public StructLike get() {
    return wrapped;
  }

  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    if (transforms[pos] != null) {
      Object value = wrapped.get(pos, Object.class);
      if (value == null) {
        // transforms function don't allow to handle null values, so just return null here.
        return null;
      } else {
        return javaClass.cast(transforms[pos].apply(value));
      }
    }
    return wrapped.get(pos, javaClass);
  }

  public InternalRecordWrapper wrap(StructLike record) {
    this.wrapped = record;
    return this;
  }

  @Override
  public int size() {
    return wrapped.size();
  }

  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException("Cannot update InternalRecordWrapper");
  }

  public InternalRecordWrapper copyFor(StructLike record) {
    return new InternalRecordWrapper(transforms).wrap(record);
  }
}
