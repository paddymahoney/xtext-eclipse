/*
 * generated by Xtext
 */
package org.eclipse.xtext.ui.tests.enumrules.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.xtext.ui.tests.enumrules.EnumRulesUiTestLanguageRuntimeModule;
import org.eclipse.xtext.ui.tests.enumrules.EnumRulesUiTestLanguageStandaloneSetup;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class EnumRulesUiTestLanguageIdeSetup extends EnumRulesUiTestLanguageStandaloneSetup {

	@Override
	public Injector createInjector() {
		return Guice.createInjector(new EnumRulesUiTestLanguageRuntimeModule(), new EnumRulesUiTestLanguageIdeModule());
	}
}
