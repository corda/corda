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


package com.intel.sgx;

import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationData;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationDataProvider;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class CConfigurationDataProvider2 extends CConfigurationDataProvider {

	public CConfigurationDataProvider2() {
	}

	@Override
	public CConfigurationData loadConfiguration(ICConfigurationDescription des,
			IProgressMonitor monitor) throws CoreException {
		return null;
	}

	@Override
	public CConfigurationData createConfiguration(
			ICConfigurationDescription des,
			ICConfigurationDescription baseDescription,
			CConfigurationData baseData, boolean clone, IProgressMonitor monitor)
			throws CoreException {
		return null;
	}

	@Override
	public void removeConfiguration(ICConfigurationDescription des,
			CConfigurationData data, IProgressMonitor monitor) {
	}
}
