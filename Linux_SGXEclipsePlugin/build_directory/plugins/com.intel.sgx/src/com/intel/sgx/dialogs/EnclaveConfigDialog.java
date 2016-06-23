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

import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.intel.sgx.handlers.EnclaveConfigHandler;

public class EnclaveConfigDialog extends SGXDialogBase {

	@SuppressWarnings("unused")
	private Shell shell;
	private EnclaveConfigHandler enclaveConfig;
	private Label statusLabel;
	private Text prodID;
	private Text isvSvn;
	private Text threadStackSize;
	private Text globalHeapSize;
	private Text tcsNum;
	private Combo tcsPolicy;
	private Button disableDebug;
	
	public EnclaveConfigDialog(Shell parentshell,EnclaveConfigHandler enclaveConfigHandler) {
		super(parentshell);
		this.shell = parentshell;
		this.enclaveConfig = enclaveConfigHandler;
		setShellStyle(SWT.RESIZE | SWT.TITLE);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {

		Composite container = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(3,false);
		container.setLayout(gridLayout);
		
		final Group groupLabel1 = new Group(container, SWT.None);
		groupLabel1.setLayout(new GridLayout(3,false));
		GridData innergrid1 = new GridData(GridData.FILL_HORIZONTAL);
		innergrid1.horizontalSpan = 3;
		groupLabel1.setLayoutData(innergrid1);
		
		Label warningLabel = new Label(groupLabel1,SWT.BEGINNING | SWT.WRAP);
		warningLabel.setText("Note: Use this Menu to change the Enclave settings.");
		
		statusLabel = new Label(container,SWT.BEGINNING | SWT.WRAP);
		GridData statusGrid = new GridData(GridData.FILL_HORIZONTAL);
		statusGrid.horizontalSpan = 3;
		statusLabel.setLayoutData(statusGrid);
		statusLabel.setText("");
		statusLabel.setForeground(JFaceResources.getColorRegistry().get(JFacePreferences.ERROR_COLOR));

		final Group groupLabel2 = new Group(container, SWT.None);
		groupLabel2.setLayout(new GridLayout(3,false));
		groupLabel2.setText("Modify the Enclave Settings here...");
		GridData innergrid = new GridData(GridData.FILL_HORIZONTAL);
		innergrid.horizontalSpan = 3;
		groupLabel2.setLayoutData(innergrid);
		
		final Label messageLabel0 = new Label(groupLabel2, SWT.NONE);
		messageLabel0.setText("Product ID:");
		messageLabel0.setLayoutData(new GridData(GridData.BEGINNING));
			
		prodID = new Text(groupLabel2, SWT.SINGLE | SWT.BORDER);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gridData.widthHint = 400;		
		prodID.setLayoutData(gridData);
		prodID.setText(enclaveConfig.prodId);
		prodID.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent modifyEvent) {
				statusLabel.setText("");
				enclaveConfig.prodId = prodID.getText();
			}
		});
		
		final Label messageLabel1 = new Label(groupLabel2, SWT.NONE);
		messageLabel1.setText("ISV SVN:");
		messageLabel1.setLayoutData(new GridData(GridData.BEGINNING));
			
		isvSvn = new Text(groupLabel2, SWT.SINGLE | SWT.BORDER);		
		isvSvn.setLayoutData(gridData);
		isvSvn.setText(enclaveConfig.isvSvn);
		isvSvn.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent modifyEvent) {
				statusLabel.setText("");
				enclaveConfig.isvSvn = isvSvn.getText();
			}
		});
		
		final Label messageLabel2 = new Label(groupLabel2, SWT.NONE);
		messageLabel2.setText("Thread Stack Size:");
		messageLabel2.setLayoutData(new GridData(GridData.BEGINNING));
		
		threadStackSize = new Text(groupLabel2, SWT.SINGLE | SWT.BORDER);
		threadStackSize.setLayoutData(gridData);
		threadStackSize.setText(enclaveConfig.threadStackSize);
		threadStackSize.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent modifyEvent) {
				enclaveConfig.threadStackSize = threadStackSize.getText();
				if(!(threadStackSize.getText().matches("0x[0-9a-fA-F]{1,}000")))
				{
					statusLabel.setText("Error: The Thread Stack Size value must be Page Aligned.");
				}
				else
				{
					if(!(enclaveConfig.globalHeapSize.matches("0x[0-9a-fA-F]{1,}000")))
						statusLabel.setText("Error: The Global Heap Size value must be Page Aligned.");
					else
						statusLabel.setText("");
				}
			}
		});
		
		final Label messageLabel3 = new Label(groupLabel2, SWT.NONE);
		messageLabel3.setText("Global Heap Size:");
		messageLabel3.setLayoutData(new GridData(GridData.BEGINNING));
		
		globalHeapSize = new Text(groupLabel2, SWT.SINGLE | SWT.BORDER);
		globalHeapSize.setLayoutData(gridData);
		globalHeapSize.setText(enclaveConfig.globalHeapSize);
		globalHeapSize.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent modifyEvent) {
				enclaveConfig.globalHeapSize = globalHeapSize.getText();
				if(!(globalHeapSize.getText().matches("0x[0-9a-fA-F]{1,}000")))
				{
					statusLabel.setText("Error: The Global Heap Size value must be Page Aligned.");
				}
				else
				{
					if(!(enclaveConfig.threadStackSize.matches("0x[0-9a-fA-F]{1,}000")))
						statusLabel.setText("Error: The Thread Stack Size value must be Page Aligned.");
					else
						statusLabel.setText("");
					
				}
			}
		});
		
		final Label messageLabel4 = new Label(groupLabel2, SWT.NONE);
		messageLabel4.setText("TCS Number:");
		messageLabel4.setLayoutData(new GridData(GridData.BEGINNING));
		
		tcsNum = new Text(groupLabel2, SWT.SINGLE | SWT.BORDER);	
		tcsNum.setLayoutData(gridData);
		tcsNum.setText(enclaveConfig.tcsNum);
		tcsNum.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent modifyEvent) {
				statusLabel.setText("");
				enclaveConfig.tcsNum = tcsNum.getText();
			}
		});
		
		final Label messageLabel5 = new Label(groupLabel2, SWT.NONE);
		messageLabel5.setText("TCS Policy:");
		messageLabel5.setLayoutData(new GridData(GridData.BEGINNING));
		
		final String[] items = {"Unbound","Bound"};
		tcsPolicy = new Combo(groupLabel2, SWT.DROP_DOWN | SWT.READ_ONLY | SWT.BORDER);
		tcsPolicy.setItems(items);
		String item = items[Integer.parseInt(enclaveConfig.tcsPolicy)];
		int index = tcsPolicy.indexOf(item);
		tcsPolicy.select(index < 0 ? 0 : index);
		tcsPolicy.setLayoutData(gridData);
		tcsPolicy.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				statusLabel.setText("");
				enclaveConfig.tcsPolicy = (tcsPolicy.getSelectionIndex() == 0 ? "0" : "1");
			}
		});
		
		
		final Label messageLabel6 = new Label(groupLabel2, SWT.NONE);
		messageLabel6.setText("Disable Debug:");
		messageLabel6.setLayoutData(new GridData(GridData.BEGINNING));
		
		disableDebug = new Button(groupLabel2,SWT.CHECK);
		GridData gridData1 = new GridData(GridData.FILL_HORIZONTAL);
		disableDebug.setLayoutData(gridData1);
		disableDebug.setSelection(enclaveConfig.disableDebug.equals("1")?true:false);
		disableDebug.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e){
				statusLabel.setText("");
				enclaveConfig.disableDebug = disableDebug.getSelection()?"1":"0";
			}
		});
		
		if(statusLabel.getText() != null){
			statusLabel.setVisible(true);
		}
		else{
			statusLabel.setVisible(false);
		}
				
		return container;
	}
	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Enclave Configuration Settings:");
	}
	
	@Override
	protected Point getInitialSize(){
		return new Point(450,400);
	}
	
	@Override
	protected
	void okPressed(){
		enclaveConfig.prodId = this.prodID.getText();
		enclaveConfig.isvSvn = this.isvSvn.getText();
		enclaveConfig.threadStackSize = this.threadStackSize.getText();
		enclaveConfig.globalHeapSize = this.globalHeapSize.getText();
		enclaveConfig.tcsNum =  this.tcsNum.getText();
		enclaveConfig.tcsPolicy = this.tcsPolicy.getSelectionIndex() == 0 ? "0" : "1";
		enclaveConfig.disableDebug = disableDebug.getSelection()?"1":"0";

		
		if((statusLabel.getText() == "") && (enclaveConfig.globalHeapSize.matches("0x[0-9a-fA-F]{1,}000")) && (enclaveConfig.threadStackSize.matches("0x[0-9a-fA-F]{1,}000")))
			super.okPressed();
	}
}
