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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IPathEntry;
import org.eclipse.cdt.core.settings.model.CIncludePathEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICFolderDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.core.templateengine.TemplateCore;
import org.eclipse.cdt.core.templateengine.process.ProcessArgument;
import org.eclipse.cdt.core.templateengine.process.ProcessFailureException;
import org.eclipse.cdt.core.templateengine.process.ProcessRunner;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.Configuration;
import org.eclipse.cdt.managedbuilder.internal.core.ManagedProject;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.intel.sgx.Activator;
import com.intel.sgx.Messages;
import com.intel.sgx.preferences.PreferenceConstants;

@SuppressWarnings("restriction")
public class CreateNativeFolders extends ProcessRunner {

	@Override
	public void process(TemplateCore template, ProcessArgument[] args,
			String processId, IProgressMonitor monitor)
			throws ProcessFailureException {
		String projectName = null;
		String[] sourceFolders = null;
		String[] outputFolders = null;

		for (ProcessArgument arg : args) {
			String argName = arg.getName();
			if (argName.equals("projectName")) {
				projectName = arg.getSimpleValue();
			} else if (argName.equals("sourceFolders")) {
				sourceFolders = arg.getSimpleArrayValue();
			} else if (argName.equals("outputFolders")) {
				outputFolders = arg.getSimpleArrayValue();
			}
		}

		if (projectName == null)
			throw new ProcessFailureException(
					Messages.CreateNativeFolders_Missing_project_name);

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (!project.exists())
			throw new ProcessFailureException(
					Messages.CreateNativeFolders_Project_does_not_exist);

		if (sourceFolders == null && outputFolders == null)
			throw new ProcessFailureException(
					Messages.CreateNativeFolders_No_folders);

		try {
			ICProject cproject = CCorePlugin.getDefault().getCoreModel()
					.create(project);
			IPathEntry[] pathEntries = cproject.getRawPathEntries();
			List<IPathEntry> newEntries = new ArrayList<IPathEntry>(
					pathEntries.length);
			for (IPathEntry pathEntry : pathEntries) {
				if (pathEntry.getEntryKind() != IPathEntry.CDT_SOURCE
						&& pathEntry.getEntryKind() != IPathEntry.CDT_OUTPUT) {
					newEntries.add(pathEntry);
				}
			}
			if (sourceFolders != null)
				for (String sourceFolder : sourceFolders) {
					IFolder folder = project.getFolder(new Path(sourceFolder));
					if (!folder.exists())
						folder.create(true, true, monitor);
					newEntries.add(CoreModel.newSourceEntry(folder
							.getFullPath()));
				}
			if (outputFolders != null)
				for (String outputFolder : outputFolders) {
					IFolder folder = project.getFolder(new Path(outputFolder));
					if (!folder.exists())
						folder.create(true, true, monitor);
					newEntries.add(CoreModel.newOutputEntry(folder
							.getFullPath()));
				}
			cproject.setRawPathEntries(
					newEntries.toArray(new IPathEntry[newEntries.size()]),
					monitor);

			// IConfiguration[] configs = managedProject.getConfigurations();
			// for(IConfiguration conf:configs){
			// managedProject.removeConfiguration(conf.getId());

			// }

			IConfiguration conSimDebug = ManagedBuildManager
					.getExtensionConfiguration("com.intel.sgx.configuration.Sim.Debug");
			IConfiguration conSimRelease = ManagedBuildManager
					.getExtensionConfiguration("com.intel.sgx.configuration.Sim.Release");
			IConfiguration conHwDebug = ManagedBuildManager
					.getExtensionConfiguration("com.intel.sgx.configuration.HW.Debug");
			IConfiguration conHwPrerelease = ManagedBuildManager
					.getExtensionConfiguration("com.intel.sgx.configuration.HW.Prerelease");
			IConfiguration conHwRelease = ManagedBuildManager
					.getExtensionConfiguration("com.intel.sgx.configuration.HW.Release");

			addConfigurationToProject(project, conSimDebug);
			addConfigurationToProject(project, conSimRelease);
			addConfigurationToProject(project, conHwDebug);
			addConfigurationToProject(project, conHwPrerelease);
			addConfigurationToProject(project, conHwRelease);
			

			changeProjectConfiguration(project, conSimDebug);

			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (CoreException e) {
			throw new ProcessFailureException(e);
		}
	}

	void addConfigurationToProject(IProject project, IConfiguration config) {
		createConfiguration(project, config);
		addSGXIncludePathsToConfiguration(project, config);
	}

	private void addSGXIncludePathsToConfiguration(IProject project,
			IConfiguration config) {
		ICProjectDescription projectDescription = CoreModel.getDefault()
				.getProjectDescription(project, true);
		ICConfigurationDescription configDecriptions[] = projectDescription
				.getConfigurations();
		for (ICConfigurationDescription configDescription : configDecriptions) {
			ICFolderDescription projectRoot = configDescription
					.getRootFolderDescription();
			
			ICLanguageSetting[] settings = projectRoot.getLanguageSettings();
			for (ICLanguageSetting setting : settings) {
				
				if (!"org.eclipse.cdt.core.gcc".equals(setting.getLanguageId()) && !"org.eclipse.cdt.core.g++".equals(setting.getLanguageId()) ) {
					continue;
				}
				List<ICLanguageSettingEntry> includes = new ArrayList<ICLanguageSettingEntry>();
				
				includes.add(new CIncludePathEntry( Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.SDK_PATH) +
						"/include/",
						ICSettingEntry.LOCAL));
			
				setting.setSettingEntries(ICSettingEntry.INCLUDE_PATH, includes);
			}
		}
		try {
			CoreModel.getDefault().setProjectDescription(project,
					projectDescription);
		} catch (CoreException e) {
			e.printStackTrace();
		}

	}

	private void createConfiguration(IProject project,
			IConfiguration config) {
		ManagedProject managedProject = (ManagedProject) ManagedBuildManager.getBuildInfo(project)
				.getManagedProject();;


		Configuration cloneConfig1 = (Configuration) config;
		Configuration cfg1 = new Configuration(managedProject, cloneConfig1,
				cloneConfig1.getId(), false, false);
		String target = cfg1.getArtifactName();
		if (target == null || target.length() == 0)
			cfg1.setArtifactName(managedProject.getDefaultArtifactName());

		cfg1.exportArtifactInfo();
		
		ManagedBuildManager.saveBuildInfo(project, true);
	}

	private void changeProjectConfiguration(IProject project,
			IConfiguration conSimDebug) {
		ICProjectDescription prjd = CCorePlugin.getDefault()
				.getProjectDescriptionManager().getProjectDescription(project);
		ICConfigurationDescription[] configs = prjd.getConfigurations();
		if (configs != null && configs.length > 0) {
			for (ICConfigurationDescription config : configs) {
				if (config.getConfiguration().getId()
						.equals(conSimDebug.getId())) {
					config.setActive();
					try {
						CoreModel.getDefault().setProjectDescription(project,
								prjd);
					} catch (CoreException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				}
			}
		}

	}

}
