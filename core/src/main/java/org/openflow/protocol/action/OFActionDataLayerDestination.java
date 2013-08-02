/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol.action;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
@SuppressFBWarnings(value={"EI_EXPOSE_REP","EI_EXPOSE_REP2"})
public class OFActionDataLayerDestination extends OFActionDataLayer {
    public OFActionDataLayerDestination() {
        super();
        super.setType(OFActionType.SET_DL_DST);
        super.setLength((short) OFActionDataLayer.MINIMUM_LENGTH);
    }
    
    public OFActionDataLayerDestination(byte[] address) {
        this();
        this.dataLayerAddress = address;
    }
}
