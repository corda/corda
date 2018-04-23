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


package com.intel.sgx.testers;

import java.util.regex.Pattern;

import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.StructuredSelection;

public class SGXPropertyTester extends PropertyTester {

	private static final Pattern isRelease = Pattern.compile(".*Release.*");
	private static final Pattern isHW = Pattern.compile(".*Hardware.*");
	private static final Pattern isPreRelease = Pattern.compile(".*Prerelease.*");;

	public SGXPropertyTester() {
		super();

		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean test(Object receiver, String property, Object[] args,
			Object expectedValue) {
		
		
		


		if (property.equals("twoStepsActive")) {
			IProject project = getProjectOfSelectedItem(receiver);
			IManagedBuildInfo buildInfo = ManagedBuildManager.getBuildInfo(project);
			return isHW(buildInfo) && isRelease(buildInfo) && !isPreRelease(buildInfo);

		} else if (property.equals("sgxNatureAdded")) {
			IProject project = getProjectOfSelectedItem(receiver);
			return true;
		} else {
			return false;
		}
	}

	private IProject getProjectOfSelectedItem(Object receiver) {
		StructuredSelection selection = (StructuredSelection) receiver;
		IResource resource = (IResource) selection.getFirstElement();
		IProject project = resource.getProject();
		return project;
	}
	
	boolean isHW(IManagedBuildInfo buildInfo){
		return isHW.matcher(buildInfo.getConfigurationName()).matches();
	}
	
	

	boolean isRelease(IManagedBuildInfo buildInfo){
		return isRelease.matcher(buildInfo.getConfigurationName()).matches();
	}
	
	boolean isPreRelease(IManagedBuildInfo buildInfo){
		return isPreRelease.matcher(buildInfo.getConfigurationName()).matches();
	}
	
}
