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

import java.io.File;
import java.util.Scanner;

import javax.swing.JOptionPane;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public abstract class TwoStepSignDialogBase extends SGXDialogBase {

	public Text enclaveFileField;
	public Text hashFileField;
	public Text externalSignPublicKeyFileField;
	public Text externalSignedHashFileField;
	public Text outputSignedEnclaveFileField;

	public TwoStepSignDialogBase(Shell parentShell) {
		super(parentShell);
		setShellStyle(SWT.RESIZE | SWT.TITLE);
		this.shell = TwoStepSignDialogBase.this.getParentShell();
		 
	}

	// for each field, a corresponding listener
	protected SelectionListener enclaveFileSelectionListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String result = enclaveFileField.getText();
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				
	
	
				dialog.setFilterExtensions(new String[]{"*.so"} );
				dialog.setFilterPath(getCurrentProjectPath().toOSString());
	
				if (result != null && !result.isEmpty()) {
					dialog.setFilterPath(new File(result).getParent());
				} else {
					dialog.setFilterPath(getCurrentProjectPath().toOSString());
				}
	
				result = dialog.open();
	
				enclaveFileField.setText(result);
	
				hashFileField.setText(result + ".hex");

				if (outputSignedEnclaveFileField != null){
					String outputSignedEnclavePath =  result;
					if(outputSignedEnclavePath.endsWith(".so"))
					{
						outputSignedEnclavePath = 
								outputSignedEnclavePath.substring(0,outputSignedEnclavePath.length() - ".so".length());
						outputSignedEnclaveFileField.setText(outputSignedEnclavePath+".signed.so");
					}
						
				}

				
				
			}
	
			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		};

		protected SelectionListener hashFileSelectionListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String result = hashFileField.getText();
				
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				
				if (result != null && !result.isEmpty()) {
					dialog.setFilterPath(new File(result).getParent());
				} else {
					dialog.setFilterPath(getCurrentProjectPath().toOSString());
				}
				
				result = dialog.open();
	
				hashFileField.setText(result);
			}
	
			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
	
		};
		
	protected SelectionListener publicKeyLocationSelectionListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String result = externalSignPublicKeyFileField.getText();
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				dialog.setFilterExtensions(new String [] {"*.pem", "*"});
				dialog.setFilterPath(getCurrentProjectPath().toOSString());
				result = dialog.open();
				externalSignPublicKeyFileField.setText(result);
			}
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}		
		};
		
	protected SelectionListener externalSignedHashFileSelectionListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String result = externalSignedHashFileField.getText();
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				dialog.setFilterPath(getCurrentProjectPath().toOSString());
				result = dialog.open();
				externalSignedHashFileField.setText(result);
			}
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}		
		};
		
		protected SelectionListener outputSignedEnclaveListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String result = outputSignedEnclaveFileField.getText();
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
	
	
				dialog.setFilterExtensions(new String[]{"*.so", } );
				dialog.setFilterPath(getCurrentProjectPath().toOSString());
	
				if (result != null && !result.isEmpty()) {
					dialog.setFilterPath(new File(result).getParent());
				} else {
					dialog.setFilterPath(getCurrentProjectPath().toOSString());
				}
	
				result = dialog.open();
	
				outputSignedEnclaveFileField.setText(result);
	
			}
	
			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		};

		
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
		}
		

}
