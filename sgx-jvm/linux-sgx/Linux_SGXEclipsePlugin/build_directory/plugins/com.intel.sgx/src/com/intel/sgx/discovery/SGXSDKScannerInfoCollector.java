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


package com.intel.sgx.discovery;

import java.util.List;
import java.util.Map;

import org.eclipse.cdt.make.core.scannerconfig.IDiscoveredPathManager.IDiscoveredPathInfo;
import org.eclipse.cdt.make.core.scannerconfig.IScannerInfoCollector3;
import org.eclipse.cdt.make.core.scannerconfig.IScannerInfoCollectorCleaner;
import org.eclipse.cdt.make.core.scannerconfig.InfoContext;
import org.eclipse.cdt.make.core.scannerconfig.ScannerInfoTypes;
import org.eclipse.cdt.managedbuilder.scannerconfig.IManagedScannerInfoCollector;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/*
 * This code has been taken from the NDK plugin for Linux. If there is an update to this code there, then refactor this code.
 */
public class SGXSDKScannerInfoCollector implements IScannerInfoCollector3,IScannerInfoCollectorCleaner,IManagedScannerInfoCollector{

	private SGXSDKDiscoveredPathInfo sgxPathInfo;
	
	@Override
	public void setProject(IProject project) {
		throw new Error("Not implemented");
	}

	@Override
	public void updateScannerConfiguration(IProgressMonitor monitor)
			throws CoreException {
		sgxPathInfo.update(monitor);		
	}

	@Override
	public IDiscoveredPathInfo createPathInfoObject() {
		return sgxPathInfo;
	}

	@Override
	public void contributeToScannerConfig(Object resource, @SuppressWarnings("rawtypes") Map scannerInfo) {
		throw new Error("Not implemented");
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getCollectedScannerInfo(Object resource, ScannerInfoTypes type) {
		throw new Error("Not implemented");
	}

	@Override
	public Map<String, String> getDefinedSymbols() {
		throw new Error("Not implemented");
	}

	@Override
	public List<String> getIncludePaths() {
		throw new Error("Not implemented");
	}

	@Override
	public void deleteAllPaths(IResource resource) {
		throw new Error("Not implemented");
	}

	@Override
	public void deleteAllSymbols(IResource resource) {
		throw new Error("Not implemented");
	}

	@Override
	public void deletePath(IResource resource, String path) {
		throw new Error("Not implemented");
	}

	@Override
	public void deleteSymbol(IResource resource, String symbol) {
		throw new Error("Not implemented");
	}

	@Override
	public void deleteAll(IResource resource) {
		sgxPathInfo.delete();
	}

	@Override
	public void setInfoContext(InfoContext context) {
		sgxPathInfo = new SGXSDKDiscoveredPathInfo(context.getProject());
	}
}
