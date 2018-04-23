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

import org.eclipse.jface.dialogs.InputDialog;

import com.intel.sgx.dialogs.TwoStepSignStep2Dialog;

// Sign
public class TwoStepSignStep2 extends TwoStepSignHandlerBase {
	
	public TwoStepSignStep2() {
	}
	
	@Override
	protected Object executeSGXStuff() throws ErrorException, CancelException {
		initializeSigntool();
		
		TwoStepSignStep2Dialog dialog = new TwoStepSignStep2Dialog(shell, this);
		if(dialog.open() != InputDialog.OK) {
			cancel();
		}
		
		executeCatSig();
		return null;
	}

}
