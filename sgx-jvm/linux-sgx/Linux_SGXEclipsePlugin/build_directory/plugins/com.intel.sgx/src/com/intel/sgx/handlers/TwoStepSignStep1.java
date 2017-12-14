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

import com.intel.sgx.dialogs.SGXDialogBase;
import com.intel.sgx.dialogs.TwoStepSignStep1Dialog1;
import com.intel.sgx.dialogs.TwoStepSignStep1Dialog2;
import com.intel.sgx.dialogs.TwoStepSignStep1Dialog3;

// Generate Hash
public class TwoStepSignStep1 extends TwoStepSignHandlerBase {

	public TwoStepSignStep1() {
	}

	@Override
	protected Object executeSGXStuff() throws ErrorException, CancelException {

		initializeSigntool();

		showDialog1();
		showDialog2();
		showDialog3();
		return null;
	}

	private void showDialog1() throws CancelException, ErrorException {

		TwoStepSignStep1Dialog1 dialog1 = new TwoStepSignStep1Dialog1(shell, this);
		if (dialog1.open() != InputDialog.OK) {
			cancel();
		}
		
		executeGenData();

	}

	private void showDialog2() throws CancelException {
		TwoStepSignStep1Dialog2 dialog2 = new TwoStepSignStep1Dialog2(shell,
				hashFile);

		if (dialog2.open() != InputDialog.OK) {
			cancel();
		}
	}

	protected void showDialog3() throws CancelException, ErrorException {
		SGXDialogBase dialog3 = new TwoStepSignStep1Dialog3(shell, this);
		if (dialog3.open() != InputDialog.OK) {
			cancel();
		}
		
		validateExternalSignPublicKeyFile();

		validateExternallySignedHashFile();

		executeCatSig();
		
	}

}
