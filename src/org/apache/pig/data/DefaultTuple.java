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
package org.apache.pig.data;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapred.JobConf;
import org.apache.pig.PigException;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.impl.io.NullableTuple;
import org.apache.pig.impl.util.ObjectSerializer;
import org.apache.pig.impl.util.TupleFormat;

/**
 * A default implementation of Tuple. This class will be created by the DefaultTupleFactory.
 */
public class DefaultTuple implements Tuple {

    protected boolean isNull = false;
    private static final long serialVersionUID = 2L;
    protected List<Object> mFields;

    /**
     * Default constructor. This constructor is public so that hadoop can call it directly. However, inside pig you
     * should never be calling this function. Use TupleFactory instead.
     */
    public DefaultTuple() {
        mFields = new ArrayList<Object>();
    }

    /**
     * Construct a tuple with a known number of fields. Package level so that callers cannot directly invoke it.
     * 
     * @param size
     *            Number of fields to allocate in the tuple.
     */
    DefaultTuple(int size) {
        mFields = new ArrayList<Object>(size);
        for (int i = 0; i < size; i++)
            mFields.add(null);
    }

    /**
     * Construct a tuple from an existing list of objects. Package level so that callers cannot directly invoke it.
     * 
     * @param c
     *            List of objects to turn into a tuple.
     */
    DefaultTuple(List<Object> c) {
        mFields = new ArrayList<Object>(c.size());

        Iterator<Object> i = c.iterator();
        int field;
        for (field = 0; i.hasNext(); field++)
            mFields.add(field, i.next());
    }

    /**
     * Construct a tuple from an existing list of objects. Package level so that callers cannot directly invoke it.
     * 
     * @param c
     *            List of objects to turn into a tuple. This list will be kept as part of the tuple.
     * @param junk
     *            Just used to differentiate from the constructor above that copies the list.
     */
    DefaultTuple(List<Object> c, int junk) {
        mFields = c;
    }

    /**
     * Make this tuple reference the contents of another. This method does not copy the underlying data. It maintains
     * references to the data from the original tuple (and possibly even to the data structure holding the data).
     * 
     * @param t
     *            Tuple to reference.
     */
    public void reference(Tuple t) {
        mFields = t.getAll();
    }

    /**
     * Find the size of the tuple. Used to be called arity().
     * 
     * @return number of fields in the tuple.
     */
    public int size() {
        return mFields.size();
    }

    /**
     * Find out if a given field is null.
     * 
     * @param fieldNum
     *            Number of field to check for null.
     * @return true if the field is null, false otherwise.
     * @throws ExecException
     *             if the field number given is greater than or equal to the number of fields in the tuple.
     */
    public boolean isNull(int fieldNum) throws ExecException {
        return (mFields.get(fieldNum) == null);
    }

    /**
     * Find the type of a given field.
     * 
     * @param fieldNum
     *            Number of field to get the type for.
     * @return type, encoded as a byte value. The values are taken from the class DataType. If the field is null, then
     *         DataType.UNKNOWN will be returned.
     * @throws ExecException
     *             if the field number is greater than or equal to the number of fields in the tuple.
     */
    public byte getType(int fieldNum) throws ExecException {
        return DataType.findType(mFields.get(fieldNum));
    }

    /**
     * Get the value in a given field.
     * 
     * @param fieldNum
     *            Number of the field to get the value for.
     * @return value, as an Object.
     * @throws ExecException
     *             if the field number is greater than or equal to the number of fields in the tuple.
     */
    public Object get(int fieldNum) throws ExecException {
        return mFields.get(fieldNum);
    }

    /**
     * Get all of the fields in the tuple as a list.
     * 
     * @return List&lt;Object&gt; containing the fields of the tuple in order.
     */
    public List<Object> getAll() {
        return mFields;
    }

    /**
     * Set the value in a given field.
     * 
     * @param fieldNum
     *            Number of the field to set the value for.
     * @param val
     *            Object to put in the indicated field.
     * @throws ExecException
     *             if the field number is greater than or equal to the number of fields in the tuple.
     */
    public void set(int fieldNum, Object val) throws ExecException {
        mFields.set(fieldNum, val);
    }

    /**
     * Append a field to a tuple. This method is not efficient as it may force copying of existing data in order to grow
     * the data structure. Whenever possible you should construct your Tuple with the newTuple(int) method and then fill
     * in the values with set(), rather than construct it with newTuple() and append values.
     * 
     * @param val
     *            Object to append to the tuple.
     */
    public void append(Object val) {
        mFields.add(val);
    }

    /**
     * Determine the size of tuple in memory. This is used by data bags to determine their memory size. This need not be
     * exact, but it should be a decent estimation.
     * 
     * @return estimated memory size.
     */
    public long getMemorySize() {
        Iterator<Object> i = mFields.iterator();
        // fixed overhead
        long empty_tuple_size = 8 /* tuple object header */
        + 8 /* isNull - but rounded to 8 bytes as total obj size needs to be multiple of 8 */
        + 8 /* mFields reference */
        + 32 /* mFields array list fixed size */;

        // rest of the fixed portion of mfields size is accounted within empty_tuple_size
        long mfields_var_size = roundToEight(4 + 4 * mFields.size());
        // in java hotspot 32bit vm, there seems to be a minimum tuple size of 96
        // which is probably from the minimum size of this array list
        mfields_var_size = Math.max(40, mfields_var_size);

        long sum = empty_tuple_size + mfields_var_size;
        while (i.hasNext()) {
            sum += getFieldMemorySize(i.next());
        }
        return sum;
    }

    /**
     * Memory size of objects are rounded to multiple of 8 bytes
     * 
     * @param i
     * @return i rounded to a equal of higher multiple of 8
     */
    private long roundToEight(long i) {
        return 8 * ((i + 7) / 8); // integer division rounds the result down
    }

    /**
     * Write a tuple of atomic values into a string. All values in the tuple must be atomic (no bags, tuples, or maps).
     * 
     * @param delim
     *            Delimiter to use in the string.
     * @return A string containing the tuple.
     * @throws ExecException
     *             if a non-atomic value is found.
     */
    public String toDelimitedString(String delim) throws ExecException {
        StringBuilder buf = new StringBuilder();
        for (Iterator<Object> it = mFields.iterator(); it.hasNext();) {
            Object field = it.next();
            buf.append(field == null ? "" : field.toString());
            if (it.hasNext())
                buf.append(delim);
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return TupleFormat.format(this);
    }

    public int compareTo(Object other) {
        if (other instanceof Tuple) {
            Tuple t = (Tuple) other;
            int mySz = mFields.size();
            int tSz = t.size();
            if (tSz < mySz) {
                return 1;
            } else if (tSz > mySz) {
                return -1;
            } else {
                for (int i = 0; i < mySz; i++) {
                    try {
                        int c = DataType.compare(mFields.get(i), t.get(i));
                        if (c != 0) {
                            return c;
                        }
                    } catch (ExecException e) {
                        throw new RuntimeException("Unable to compare tuples", e);
                    }
                }
                return 0;
            }
        } else {
            return DataType.compare(this, other);
        }
    }

    public static class DefaultTupleRawComparator extends WritableComparator implements TupleRawComparator {
        private final Log mLog = LogFactory.getLog(getClass());
        private boolean[] mAsc;
        private boolean mWholeTuple;
        private boolean mHasNullField;
        private TupleFactory mFact;

        public DefaultTupleRawComparator() {
            super(DefaultTuple.class);
        }

        @Override
        public Configuration getConf() {
            return null;
        }

        @Override
        public void setConf(Configuration conf) {
            if (!(conf instanceof JobConf)) {
                mLog.warn("Expected jobconf in setConf, got " + conf.getClass().getName());
                return;
            }
            JobConf jconf = (JobConf) conf;
            try {
                mAsc = (boolean[]) ObjectSerializer.deserialize(jconf.get("pig.sortOrder"));
            } catch (IOException ioe) {
                mLog.error("Unable to deserialize pig.sortOrder " + ioe.getMessage());
                throw new RuntimeException(ioe);
            }
            if (mAsc == null) {
                mAsc = new boolean[1];
                mAsc[0] = true;
            }
            // If there's only one entry in mAsc, it means it's for the whole
            // tuple. So we can't be looking for each column.
            mWholeTuple = (mAsc.length == 1);
            mFact = TupleFactory.getInstance();
        }

        @Override
        public boolean hasComparedTupleNull() {
            return mHasNullField;
        }

        /**
         * Compare two DefaultTuples as raw bytes. We assume the Tuples are NOT PigNullableWritable, so client classes
         * need to deal with Null and Index.
         */
        @Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
            ByteBuffer bb1 = ByteBuffer.wrap(b1, s1, l1);
            ByteBuffer bb2 = ByteBuffer.wrap(b2, s2, l2);
            int rc = compareDefaultTuple(bb1, bb2, true); // FIXME adjust for secondary sort asc
            return rc;
        }

        /**
         * Compare two DefaultTuples as raw bytes.
         */
        private int compareDefaultTuple(ByteBuffer bb1, ByteBuffer bb2, boolean topLevel) {
            mHasNullField = false;
            // store the position in case of deserialization
            int s1 = bb1.position();
            int s2 = bb2.position();
            int rc = 0;
            byte tupleType1 = bb1.get();
            byte tupleType2 = bb2.get();
            assert (tupleType1 == tupleType2 && tupleType1 == DataType.TUPLE);
            // first compare sizes
            int sz1 = bb1.getInt();
            int sz2 = bb2.getInt();
            if (sz1 > sz2) {
                return 1;
            } else if (sz1 < sz2) {
                return -1;
            } else {
                // if sizes are the same, compare field by field
                for (int i = 0; i < sz1 && rc == 0; i++) {
                    byte dt1 = bb1.get();
                    byte dt2 = bb2.get();
                    if (dt1 == dt2) {
                        switch (dt1) {
                        case DataType.NULL:
                            if (topLevel) // we are scanning the top-level Tuple (original call)
                                mHasNullField = true;
                            rc = 0;
                            break;
                        case DataType.BOOLEAN:
                        case DataType.BYTE:
                            byte bv1 = bb1.get();
                            byte bv2 = bb2.get();
                            rc = (bv1 < bv2 ? -1 : (bv1 == bv2 ? 0 : 1));
                            break;
                        case DataType.INTEGER:
                            int iv1 = bb1.getInt();
                            int iv2 = bb2.getInt();
                            rc = (iv1 < iv2 ? -1 : (iv1 == iv2 ? 0 : 1));
                            break;
                        case DataType.LONG:
                            long lv1 = bb1.getLong();
                            long lv2 = bb2.getLong();
                            rc = (lv1 < lv2 ? -1 : (lv1 == lv2 ? 0 : 1));
                            break;
                        case DataType.FLOAT:
                            float fv1 = bb1.getFloat();
                            float fv2 = bb2.getFloat();
                            rc = Float.compare(fv1, fv2);
                            break;
                        case DataType.DOUBLE:
                            double dv1 = bb1.getDouble();
                            double dv2 = bb2.getDouble();
                            rc = Double.compare(dv1, dv2);
                            break;
                        case DataType.BYTEARRAY:
                            int basz1 = bb1.getInt();
                            int basz2 = bb2.getInt();
                            byte[] ba1 = new byte[basz1];
                            byte[] ba2 = new byte[basz2];
                            bb1.get(ba1);
                            bb2.get(ba2);
                            rc = DataByteArray.compare(ba1, ba2);
                            break;
                        case DataType.CHARARRAY:
                        case DataType.BIGCHARARRAY:
                            int casz1 = (dt1 == DataType.CHARARRAY) ? bb1.getShort() : bb1.getInt();
                            int casz2 = (dt1 == DataType.CHARARRAY) ? bb2.getShort() : bb2.getInt();
                            byte[] ca1 = new byte[casz1];
                            byte[] ca2 = new byte[casz2];
                            bb1.get(ca1);
                            bb2.get(ca2);
                            String str1 = null,
                            str2 = null;
                            try {
                                str1 = new String(ca1, DataReaderWriter.UTF8);
                                str2 = new String(ca2, DataReaderWriter.UTF8);
                            } catch (UnsupportedEncodingException uee) {
                                mLog.warn("Unsupported string encoding", uee);
                                uee.printStackTrace();
                            }
                            if (str1 != null && str2 != null)
                                rc = str1.compareTo(str2);
                            break;
                        case DataType.TUPLE:
                            // put back the cursor to before DataType.TUPLE
                            bb1.position(bb1.position() - 1);
                            bb2.position(bb2.position() - 1);
                            rc = compareDefaultTuple(bb1, bb2, false);
                            break;
                        default:
                            mLog.info("Unsupported DataType for binary comparison, switching to object deserialization: "
                                    + DataType.genTypeToNameMap().get(dt1) + "(" + dt1 + ")");
                            Tuple t1 = mFact.newTuple();
                            Tuple t2 = mFact.newTuple();
                            try {
                                t1.readFields(new DataInputStream(
                                        new ByteArrayInputStream(bb1.array(), s1, bb1.limit())));
                                t2.readFields(new DataInputStream(
                                        new ByteArrayInputStream(bb2.array(), s2, bb2.limit())));
                            } catch (IOException ioe) {
                                mLog.error("Unable to instantiate tuples for comparison: " + ioe.getMessage());
                                throw new RuntimeException(ioe.getMessage(), ioe);
                            }
                            // delegate to compareTuple
                            return compareTuple(t1, t2);
                        }
                    } else { // compare DataTypes
                        if (dt1 < dt2)
                            rc = -1;
                        else
                            rc = 1;
                    }
                    // flip if the order is descending
                    if (rc != 0) {
                        if (!mWholeTuple && !mAsc[i])
                            rc *= -1;
                        else if (mWholeTuple && !mAsc[0])
                            rc *= -1;
                    }
                }
            }
            return rc;
        }

        @Override
        public int compare(Object o1, Object o2) {
            NullableTuple nt1 = (NullableTuple) o1;
            NullableTuple nt2 = (NullableTuple) o2;
            int rc = 0;

            // if either are null, handle differently
            if (!nt1.isNull() && !nt2.isNull()) {
                rc = compareTuple((Tuple) nt1.getValueAsPigType(), (Tuple) nt2.getValueAsPigType());
            } else {
                // for sorting purposes two nulls are equal
                if (nt1.isNull() && nt2.isNull())
                    rc = 0;
                else if (nt1.isNull())
                    rc = -1;
                else
                    rc = 1;
                if (mWholeTuple && !mAsc[0])
                    rc *= -1;
            }
            return rc;
        }

        private int compareTuple(Tuple t1, Tuple t2) {
            int sz1 = t1.size();
            int sz2 = t2.size();
            if (sz2 < sz1) {
                return 1;
            } else if (sz2 > sz1) {
                return -1;
            } else {
                for (int i = 0; i < sz1; i++) {
                    try {
                        int c = DataType.compare(t1.get(i), t2.get(i));
                        if (c != 0) {
                            if (!mWholeTuple && !mAsc[i])
                                c *= -1;
                            else if (mWholeTuple && !mAsc[0])
                                c *= -1;
                            return c;
                        }
                    } catch (ExecException e) {
                        throw new RuntimeException("Unable to compare tuples", e);
                    }
                }
                return 0;
            }
        }

    }

    @Override
    public boolean equals(Object other) {
        return (compareTo(other) == 0);
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (Iterator<Object> it = mFields.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o != null) {
                hash = 31 * hash + o.hashCode();
            }
        }
        return hash;
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(DataType.TUPLE);
        int sz = size();
        out.writeInt(sz);
        for (int i = 0; i < sz; i++) {
            DataReaderWriter.writeDatum(out, mFields.get(i));
        }
    }

    public void readFields(DataInput in) throws IOException {
        // Clear our fields, in case we're being reused.
        mFields.clear();

        // Make sure it's a tuple.
        byte b = in.readByte();
        if (b != DataType.TUPLE) {
            int errCode = 2112;
            String msg = "Unexpected data while reading tuple " + "from binary file.";
            throw new ExecException(msg, errCode, PigException.BUG);
        }
        // Read the number of fields
        int sz = in.readInt();
        for (int i = 0; i < sz; i++) {
            try {
                append(DataReaderWriter.readDatum(in));
            } catch (ExecException ee) {
                throw ee;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private long getFieldMemorySize(Object o) {
        // 12 is added to each to account for the object overhead and the
        // pointer in the tuple.
        switch (DataType.findType(o)) {
        case DataType.BYTEARRAY: {
            byte[] bytes = ((DataByteArray) o).get();
            // bytearray size including rounding to 8 bytes
            long byte_array_sz = roundToEight(bytes.length + 12);

            return byte_array_sz + 16 /* 16 is additional size of DataByteArray */;
        }

        case DataType.CHARARRAY: {
            String s = (String) o;
            // See PIG-1443 for a reference for this formula
            return roundToEight((s.length() * 2) + 38);
        }

        case DataType.TUPLE: {
            Tuple t = (Tuple) o;
            return t.getMemorySize();
        }

        case DataType.BAG: {
            DataBag b = (DataBag) o;
            return b.getMemorySize();
        }

        case DataType.INTEGER:
            return 4 + 8 + 4/* +4 to round to 8 bytes */;

        case DataType.LONG:
            return 8 + 8;

        case DataType.MAP: {
            Map<String, Object> m = (Map<String, Object>) o;
            Iterator<Map.Entry<String, Object>> i = m.entrySet().iterator();
            long sum = 0;
            while (i.hasNext()) {
                Map.Entry<String, Object> entry = i.next();
                sum += getFieldMemorySize(entry.getKey());
                sum += getFieldMemorySize(entry.getValue());
            }
            // based on experiments on 32 bit Java HotSpot VM
            // size of map with 0 entries is 120 bytes
            // each additional entry have around 24 bytes overhead at
            // small number of entries. At larger number of entries, the
            // overhead is around 32 bytes, probably because of the expanded
            // data structures in anticapation of more entries being added
            return sum + m.size() * 32 + 120;
        }

        case DataType.FLOAT:
            return 4 + 8 + 4/* +4 to round to 8 bytes */;

        case DataType.DOUBLE:
            return 8 + 8;

        case DataType.BOOLEAN:
            // boolean takes 1 byte , +7 to round it to 8
            return 1 + 8 + 7;

        case DataType.NULL:
            return 0;

        default:
            // ??
            return 12;
        }
    }

    /**
     * @return true if this Tuple is null
     */
    public boolean isNull() {
        return isNull;
    }

    /**
     * @param isNull
     *            boolean indicating whether this tuple is null
     */
    public void setNull(boolean isNull) {
        this.isNull = isNull;
    }
    
    public static Class<? extends TupleRawComparator> getComparatorClass() {
        return DefaultTupleRawComparator.class;
    }
}
