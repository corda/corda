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


package com.intel.sgx.templates;

import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.process.ProcessArgument;
import org.eclipse.cdt.core.templateengine.process.ProcessFailureException;
import org.eclipse.cdt.core.templateengine.process.ProcessRunner;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import com.intel.sgx.Activator;
import com.intel.sgx.natures.SGXNature;

public class SetCCNature extends ProcessRunner {

	public SetCCNature() {
	}

	@Override
	public void process(TemplateCore template, ProcessArgument[] args,
			String processId, IProgressMonitor monitor)
			throws ProcessFailureException {
		String projectName = null;
		IProject project = null;
		
		for(ProcessArgument arg: args){
			String argName = arg.getName();
			if(argName.equals("projectName")){
				projectName = arg.getSimpleValue();
			}
		}
		
		
		project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		IWorkspace workspace = project.getWorkspace();
		IProjectDescription description;
		try {
			description = project.getDescription();
		
			String[] natures = description.getNatureIds();
			String[] newNatures = new String[natures.length+1];
			System.arraycopy(natures,0,newNatures,0,natures.length);
			newNatures[natures.length] = SGXNature.NATURE_ID;
			IStatus status = workspace.validateNatureSet(newNatures);
			
			if(status.getCode() == IStatus.OK)
			{
				
				description.setNatureIds(newNatures);
				project.setDescription(description, null);
			}
			else {
				System.err.println("Incorrect Project Nature. Please check Project Settings.");
			}
		} catch (CoreException e) {
			Activator.log(e);
			e.printStackTrace();
		}
	}
}
