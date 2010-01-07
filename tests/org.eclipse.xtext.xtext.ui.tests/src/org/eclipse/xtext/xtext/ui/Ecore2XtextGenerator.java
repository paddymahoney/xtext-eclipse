package org.eclipse.xtext.xtext.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.mwe.core.WorkflowContext;
import org.eclipse.emf.mwe.core.issues.Issues;
import org.eclipse.emf.mwe.core.lib.AbstractWorkflowComponent2;
import org.eclipse.emf.mwe.core.monitor.ProgressMonitor;
import org.eclipse.xpand2.XpandExecutionContextImpl;
import org.eclipse.xpand2.XpandFacade;
import org.eclipse.xpand2.output.Outlet;
import org.eclipse.xpand2.output.OutputImpl;
import org.eclipse.xtend.type.impl.java.JavaBeansMetaModel;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.xtext.ui.wizard.ecore2xtext.Ecore2XtextProjectInfo;

public class Ecore2XtextGenerator extends AbstractWorkflowComponent2 {

	protected final Log log = LogFactory.getLog(getClass());
	
	private ResourceSet resourceSet;

	private String genPath;

	private String rootElementClassName;
	
	private String languageName;

	private Ecore2XtextProjectInfo xtextProjectInfo;

	public Ecore2XtextGenerator() {
		resourceSet = new XtextResourceSet();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("ecore", new XMIResourceFactoryImpl());
	}

	public void addEcoreFile(String uri) {
		if(uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		if(languageName == null) {
			languageName = uri.replaceAll("/", ".").substring(0, uri.lastIndexOf('.'));
		}
		resourceSet.getResource(URI.createURI("classpath:/" + uri), true);
	}

	public void setGenPath(String genPath) {
		this.genPath = genPath;
	}

	public void setRootElement(String rootElementClassName) {
		this.rootElementClassName = rootElementClassName;
	}

	public void setLanguageName(String languageName) {
		this.languageName = languageName;
	}
	
	@Override
	protected void invokeInternal(WorkflowContext ctx, ProgressMonitor monitor,
			Issues issues) {
		createXtextProjectInfo(issues);
		XpandExecutionContextImpl executionContext = initializeExecutionContext(issues);
		if (issues.hasErrors()) {
			return;
		}
		XpandFacade xpandFacade = XpandFacade.create(executionContext);
		xpandFacade
				.evaluate2(
						"org::eclipse::xtext::xtext::ui::wizard::ecore2xtext::Ecore2Xtext::grammar",
						xtextProjectInfo, null);
		Outlet outlet = executionContext.getOutput().getOutlet(null);
		log.info("Written " + outlet.getFilesWrittenAndClosed() + " files");

	}

	private XpandExecutionContextImpl initializeExecutionContext(Issues issues) {
		OutputImpl output = new OutputImpl();
		Outlet outlet = new Outlet();
		outlet.setAppend(false);
		outlet.setFileEncoding(System.getProperty("file.encoding"));
		outlet.setOverwrite(true);
		outlet.setPath(genPath);
		output.addOutlet(outlet);
		XpandExecutionContextImpl executionContext = new XpandExecutionContextImpl(
				output, null);
		executionContext.registerMetaModel(new JavaBeansMetaModel());
		return executionContext;
	}

	private void createXtextProjectInfo(Issues issues) {
		if (xtextProjectInfo == null) {
			xtextProjectInfo = new Ecore2XtextProjectInfo();
			List<EPackage> ePackages = new ArrayList<EPackage>();
			EClass rootElementClass = null;
			for (Iterator<Notifier> i = resourceSet.getAllContents(); i
					.hasNext();) {
				Notifier next = i.next();
				if (next instanceof EPackage) {
					ePackages.add((EPackage) next);
					if (rootElementClass == null) {
						EClassifier rootElementClassifier = ((EPackage) next)
								.getEClassifier(rootElementClassName);
						if (rootElementClassifier instanceof EClass) {
							rootElementClass = (EClass) rootElementClassifier;
						}
					}
				}
			}
			if (ePackages.isEmpty()) {
				issues.addError("No EPackages found");
			} else {
				xtextProjectInfo.setDefaultEPackage(ePackages.get(0));
			}
			if (rootElementClass == null) {
				issues.addError("No rootElement EClass " + rootElementClassName + " found");
			}
			if(languageName == null) {
				issues.addError("languageName must be set");
			}
			xtextProjectInfo.setLanguageName(languageName);
			xtextProjectInfo.setEPackagesForRules(ePackages);
			xtextProjectInfo.setRootElementClass(rootElementClass);
		}
	}

	@Override
	protected void checkConfigurationInternal(Issues issues) {
		if (genPath == null) {
			issues.addError("genPath not set");
		}
		if(rootElementClassName == null) {
			issues.addError("rootElement not set");
		}
		if(!issues.hasErrors()) {
			createXtextProjectInfo(issues);
		}
	}
}
