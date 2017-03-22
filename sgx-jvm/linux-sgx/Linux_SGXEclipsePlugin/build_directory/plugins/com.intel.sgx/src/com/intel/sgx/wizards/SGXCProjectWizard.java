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


package com.intel.sgx.wizards;

import java.net.URI;
import java.util.Map;

import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.TemplateEngine;
import org.eclipse.cdt.ui.wizards.CProjectWizard;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.intel.sgx.natures.SGXNature;

public class SGXCProjectWizard extends CProjectWizard {

	private IProject project;

	@Override
	protected boolean setCreated() throws CoreException {
		boolean result = super.setCreated();
		doIt(project, new NullProgressMonitor());
		return result;
	}

	@Override
	public boolean performFinish() {

		return super.performFinish();
	}

	@Override
	public void setInitializationData(IConfigurationElement config,
			String propertyName, Object data) throws CoreException {
		// TODO Auto-generated method stub
		super.setInitializationData(config, propertyName, data);
	}

	@Override
	public IProject createIProject(String name, URI location)
			throws CoreException {
		// TODO Auto-generated method stub
		return super.createIProject(name, location);

	}

	@Override
	public IProject createIProject(String name, URI location,
			IProgressMonitor monitor) throws CoreException {
		project = super.createIProject(name, location, monitor);
		return project;
	}

	@Override
	public String[] getExtensions() {
		// TODO Auto-generated method stub
		return super.getExtensions();
	}

	public SGXCProjectWizard() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String[] getNatures() {
		// TODO Auto-generated method stub
		return super.getNatures();
	}

	@Override
	protected IProject continueCreation(IProject prj) {
		// TODO Auto-generated method stub
		return super.continueCreation(prj);
	}

	@Override
	public String[] getContentTypeIDs() {
		// TODO Auto-generated method stub
		return super.getContentTypeIDs();
	}

	@Override
	public IProject getProject(boolean defaults) {
		// TODO Auto-generated method stub
		return super.getProject(defaults);
	}

	@Override
	public String[] getLanguageIDs() {
		// TODO Auto-generated method stub
		return super.getLanguageIDs();
	}

	void doIt(IProject project, IProgressMonitor monitor) throws CoreException {
		TemplateCore template = TemplateEngine.getDefault().getTemplateById(
				"AddSGXNature");
		Map<String, String> valueStore = template.getValueStore();
		valueStore.put("projectName", project.getName());
		valueStore.put("baseName", project.getName());
		template.executeTemplateProcesses(monitor, false);

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProjectDescription description = project.getDescription();
		String[] natures = description.getNatureIds();
		for (String nature : natures) {
		}

		String[] newNatures = new String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = SGXNature.NATURE_ID;
		IStatus status = workspace.validateNatureSet(newNatures);

		if (status.getCode() == IStatus.OK) {
			description.setNatureIds(newNatures);
			project.setDescription(description, null);
		} else {
			System.err
					.println("Incorrect Project Nature. Please check Project Settings.");// TODO
																							// throw
																							// an
																							// exception
																							// here.
			System.err.println("Status is: " + status.getCode());
		}

	}
}
