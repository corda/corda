///////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Intel Corporation.				 //
// 									 //
// All rights reserved. This program and the accompanying materials	 //
// are made available under the terms of the Eclipse Public License v1.0 //
// which accompanies this distribution, and is available at		 //
// http://www.eclipse.org/legal/epl-v10.html				 //
// 									 //
// Contributors:							 //
//     Intel Corporation - initial implementation and documentation	 //
///////////////////////////////////////////////////////////////////////////


package com.intel.sgx.handlers;

import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.ui.wizards.STDWizardHandler;

public class SGXSDKWizardHandler extends STDWizardHandler {

	public SGXSDKWizardHandler() {
		super(null, null);
	}

	
    @Override
    public IToolChain[] getSelectedToolChains() {
        IToolChain[] tcs = ManagedBuildManager.getRealToolChains();
        for (IToolChain tc : tcs) {
            if (tc.getId().equals("com.intel.sgx.SGXtoolChain"))
                return new IToolChain[] {
                    tc
                };
        }
        return super.getSelectedToolChains();
    }
}
