/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package org.projectfloodlight.core.util;

public class MutableInteger extends Number {
    private static final long serialVersionUID = 1L;
    int mutableInt;
    
    public MutableInteger(int value) {
        this.mutableInt = value;
    }
    
    public void setValue(int value) {
        this.mutableInt = value;
    }
    
    @Override
    public double doubleValue() {
        return (double) mutableInt;
    }

    @Override
    public float floatValue() {
        // TODO Auto-generated method stub
        return (float) mutableInt;
    }

    @Override
    public int intValue() {
        // TODO Auto-generated method stub
        return mutableInt;
    }

    @Override
    public long longValue() {
        // TODO Auto-generated method stub
        return (long) mutableInt;
    }

}