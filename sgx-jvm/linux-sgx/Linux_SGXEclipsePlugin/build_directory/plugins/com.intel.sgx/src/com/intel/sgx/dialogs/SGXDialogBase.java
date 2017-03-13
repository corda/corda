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


package com.intel.sgx.dialogs;

import java.io.InputStream;
import java.util.Scanner;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog;

import com.intel.sgx.Activator;

public abstract class SGXDialogBase extends Dialog {

	protected Shell shell;
	public Text configFileField;
	public static FilteredResourcesSelectionDialog dialogForConfig(Shell shell) {
		// final IContainer container = ResourcesPlugin.getWorkspace().getRoot();
		
		final IContainer container = SGXDialogBase.getCurrentProject();

		FilteredResourcesSelectionDialog d = new FilteredResourcesSelectionDialog(
				shell, false, container, IResource.FILE) {
			{
				setInitialPattern("**");
			}

			@Override
			protected IStatus validateItem(Object item) {
				// return Status.OK_STATUS;
				IFile f = (IFile) item;
				if (f.getParent() instanceof IProject) {
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							"The selected resource has to be part of the source folder");
				}
				
				
				return super.validateItem(item);
			}

			protected ItemsFilter createFilter() {
				return new ResourceFilter(container, true, IResource.FILE) {

					@Override
					public boolean matchItem(Object item) {
						return isConfigFile(item);
					}

					private boolean isConfigFile(Object item) {
						if (!(item.toString().endsWith(".xml") && super
								.matchItem(item))) {
							return false;
						}
						try {
							IFile iFile = (IFile) item;
							return streamContainsString(iFile.getContents(),
									"<EnclaveConfiguration>");
						} catch (Throwable e) {
							return false;
						}
					}

				};

			}

			public boolean streamContainsString(InputStream is,
					String searchString) {
				Scanner streamScanner = new Scanner(is);
				if (streamScanner.findWithinHorizon(searchString, 0) != null) {
					return true;
				} else {
					return false;
				}
			}

		};
		return d;
	}


	
	protected SelectionListener configFileSelectionListener = new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent event) {
		
					FilteredResourcesSelectionDialog d = dialogForConfig(shell);
					d.setTitle("Select Config File");
					if (d.open() == Dialog.OK) {
						IFile target = (IFile) d.getResult()[0];
						configFileField.setText(target.getLocation().toOSString());
					}
					;
				}
		
				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
					// TODO Auto-generated method stub
		
				}
				
			};

	public SGXDialogBase(Shell parentShell) {
		super(parentShell);
	}

	public SGXDialogBase(IShellProvider parentShell) {
		super(parentShell);
	}

	protected Text addGroup(Composite composite, String title, String subtitle,
			String label, String selectButtonLabel, SelectionListener selectionListener) {
				final Group container = new Group(composite, SWT.None);
				container.setLayout(new GridLayout(3, false));
				GridData innergrid1 = new GridData(GridData.FILL_HORIZONTAL);
				innergrid1.horizontalSpan = 3;
				container.setLayoutData(innergrid1);
				container.setText(title);
			
				final Label messageLabel = new Label(container, SWT.NONE);
				messageLabel.setLayoutData(new GridData(GridData.BEGINNING,
						GridData.CENTER, false, false, 3, 1));
				messageLabel.setText(subtitle);
			
				final Label messageLabel1 = new Label(container, SWT.NONE);
				messageLabel1.setText(label);
				messageLabel1.setLayoutData(new GridData(GridData.BEGINNING));
			
				Text directoryNameField = new Text(container, SWT.SINGLE | SWT.BORDER);
				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 1;
				gridData.widthHint = 400;
				directoryNameField.setLayoutData(gridData);
			
				final Button selectButton = new Button(container, SWT.PUSH);
				selectButton.setText(selectButtonLabel);
				GridData buttonGridData = new GridData(GridData.END);
				buttonGridData.horizontalAlignment = SWT.RIGHT;
				buttonGridData.horizontalSpan = 1;
				buttonGridData.minimumWidth = 120;
				selectButton.setLayoutData(buttonGridData);
				selectButton.addSelectionListener(selectionListener);
				return directoryNameField;
			}

	
	public IPath getCurrentProjectPath() {
		IProject project = getCurrentProject();
		
		IPath path = null;
		if (project != null) {
			path = project.getLocation();
		}
		return path;
	}

	static public IProject getCurrentProject() {
		IProject project = null;

		
		IWorkbenchWindow window = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow();
		if (window != null) {
			IStructuredSelection selection = (IStructuredSelection) window
					.getSelectionService().getSelection();
			Object firstElement = selection.getFirstElement();
			if (firstElement instanceof IAdaptable) {
				project = (IProject) ((IAdaptable) firstElement)
						.getAdapter(IProject.class);
			}
		}
		return project;
	}
	
	@Override
	protected void configureShell(Shell newShell){
		super.configureShell(newShell);
	}

	protected void addLabel(final Group container, String labelText) {
		final Label messageLabel = new Label(container, SWT.NONE);
		messageLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 3, 1));
		messageLabel.setText(labelText);
	}

}
