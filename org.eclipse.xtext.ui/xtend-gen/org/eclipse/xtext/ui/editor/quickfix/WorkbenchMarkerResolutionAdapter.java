/**
 * Copyright (c) 2017 TypeFox GmbH (http://www.typefox.io) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.xtext.ui.editor.quickfix;

import com.google.common.base.Objects;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Consumer;
import org.apache.log4j.Logger;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;
import org.eclipse.xtend.lib.annotations.Accessors;
import org.eclipse.xtext.ide.serializer.IChangeSerializer;
import org.eclipse.xtext.ide.serializer.impl.ChangeSerializer;
import org.eclipse.xtext.ui.editor.model.edit.IContextFreeModification;
import org.eclipse.xtext.ui.editor.model.edit.IModification;
import org.eclipse.xtext.ui.editor.quickfix.IssueResolution;
import org.eclipse.xtext.ui.editor.quickfix.MarkerResolutionGenerator;
import org.eclipse.xtext.ui.refactoring2.ChangeConverter;
import org.eclipse.xtext.ui.refactoring2.LtkIssueAcceptor;
import org.eclipse.xtext.ui.resource.IResourceSetProvider;
import org.eclipse.xtext.ui.util.IssueUtil;
import org.eclipse.xtext.validation.Issue;
import org.eclipse.xtext.xbase.lib.Conversions;
import org.eclipse.xtext.xbase.lib.Exceptions;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.ListExtensions;
import org.eclipse.xtext.xbase.lib.Pair;
import org.eclipse.xtext.xbase.lib.Pure;

/**
 * MarkerResolution which extends WorkbenchMarkerResolution and can be applied on multiple markers.
 * 
 * @author dhuebner - Initial contribution and API
 * @since 2.13
 */
@SuppressWarnings("all")
public class WorkbenchMarkerResolutionAdapter extends WorkbenchMarkerResolution {
  @Inject
  private MarkerResolutionGenerator resolutionsGenerator;
  
  @Inject
  private IssueUtil issueUtil;
  
  @Inject
  private IResourceSetProvider resSetProvider;
  
  @Inject
  private Provider<ChangeSerializer> serializerProvider;
  
  private final static Logger LOG = Logger.getLogger(WorkbenchMarkerResolutionAdapter.class);
  
  @Accessors
  private IssueResolution primaryResolution;
  
  @Accessors
  private IMarker primaryMarker;
  
  @Override
  public IMarker[] findOtherMarkers(final IMarker[] markers) {
    final Function1<IMarker, Boolean> _function = (IMarker it) -> {
      String _code = this.issueUtil.getCode(this.primaryMarker);
      String _code_1 = this.issueUtil.getCode(it);
      return Boolean.valueOf(Objects.equal(_code, _code_1));
    };
    return ((IMarker[])Conversions.unwrapArray(IterableExtensions.<IMarker>filter(((Iterable<IMarker>)Conversions.doWrapArray(markers)), _function), IMarker.class));
  }
  
  @Override
  public String getLabel() {
    return this.primaryResolution.getLabel();
  }
  
  @Override
  public void run(final IMarker[] markers, final IProgressMonitor monitor) {
    try {
      new WorkspaceModifyOperation() {
        @Override
        protected void execute(final IProgressMonitor monitor) throws CoreException, InvocationTargetException, InterruptedException {
          monitor.beginTask("Applying resolutions", ((List<IMarker>)Conversions.doWrapArray(markers)).size());
          final Consumer<Pair<EObject, IssueResolution>> _function = (Pair<EObject, IssueResolution> it) -> {
            monitor.setTaskName("Applying resolution");
            WorkbenchMarkerResolutionAdapter.this.run(it.getKey(), it.getValue(), monitor);
            monitor.internalWorked(1);
          };
          WorkbenchMarkerResolutionAdapter.this.collectResolutions(monitor, markers).forEach(_function);
          monitor.done();
        }
      }.run(monitor);
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  @Override
  public void run(final IMarker marker) {
    boolean _exists = marker.exists();
    boolean _not = (!_exists);
    if (_not) {
      return;
    }
    final Pair<EObject, IssueResolution> resolutionData = this.resolution(marker);
    NullProgressMonitor _nullProgressMonitor = new NullProgressMonitor();
    this.run(resolutionData.getKey(), resolutionData.getValue(), _nullProgressMonitor);
  }
  
  public List<Pair<EObject, IssueResolution>> collectResolutions(final IProgressMonitor monitor, final IMarker... markers) {
    final Function1<IMarker, Pair<EObject, IssueResolution>> _function = (IMarker marker) -> {
      Pair<EObject, IssueResolution> _xblockexpression = null;
      {
        boolean _isCanceled = monitor.isCanceled();
        if (_isCanceled) {
          throw new OperationCanceledException();
        }
        _xblockexpression = this.resolution(marker);
      }
      return _xblockexpression;
    };
    return IterableExtensions.<Pair<EObject, IssueResolution>>toList(IterableExtensions.<Pair<EObject, IssueResolution>>filterNull(ListExtensions.<IMarker, Pair<EObject, IssueResolution>>map(((List<IMarker>)Conversions.doWrapArray(markers)), _function)));
  }
  
  public Pair<EObject, IssueResolution> resolution(final IMarker marker) {
    final URI uri = this.issueUtil.getUriToProblem(marker);
    final Resource resource = this.resSetProvider.get(marker.getResource().getProject()).getResource(uri.trimFragment(), true);
    final EObject targetObject = resource.getEObject(uri.fragment());
    if ((targetObject != null)) {
      final Issue issue = this.issueUtil.createIssue(marker);
      final Function1<IssueResolution, Boolean> _function = (IssueResolution it) -> {
        return Boolean.valueOf(this.isSameResolution(it, this.primaryResolution));
      };
      final IssueResolution resolution = IterableExtensions.<IssueResolution>head(IterableExtensions.<IssueResolution>filter(this.resolutionsGenerator.getResolutionProvider().getResolutions(issue), _function));
      if ((resolution == null)) {
        String _code = issue.getCode();
        String _plus = ("Resolution missing for " + _code);
        WorkbenchMarkerResolutionAdapter.LOG.warn(_plus);
      }
      IModification _modification = resolution.getModification();
      if ((_modification instanceof IContextFreeModification.PreInitializedModification)) {
        IModification _modification_1 = resolution.getModification();
        ((IContextFreeModification.PreInitializedModification) _modification_1).init(targetObject);
      }
      return Pair.<EObject, IssueResolution>of(targetObject, resolution);
    }
    return null;
  }
  
  @Inject
  private ChangeConverter.Factory converterFactory;
  
  @Inject
  private LtkIssueAcceptor issueAcceptor;
  
  public void run(final EObject targetObject, final IssueResolution resolution, final IProgressMonitor monitor) {
    try {
      final ChangeSerializer serializer = this.serializerProvider.get();
      final ChangeConverter converter = this.converterFactory.create(resolution.getLabel(), null, this.issueAcceptor);
      final IChangeSerializer.IModification<EObject> _function = (EObject it) -> {
        IModification _modification = resolution.getModification();
        ((IContextFreeModification) _modification).apply(targetObject);
      };
      serializer.<EObject>addModification(targetObject, _function);
      serializer.applyModifications(converter);
      final Change ltkChange = converter.getChange();
      ltkChange.initializeValidationData(monitor);
      new PerformChangeOperation(ltkChange).run(monitor);
      String _label = resolution.getLabel();
      String _plus = ("Resolution applied for " + _label);
      String _plus_1 = (_plus + " in ");
      String _plus_2 = (_plus_1 + targetObject);
      WorkbenchMarkerResolutionAdapter.LOG.debug(_plus_2);
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  @Override
  public String getDescription() {
    return this.primaryResolution.getDescription();
  }
  
  @Override
  public Image getImage() {
    return this.resolutionsGenerator.getImage(this.primaryResolution);
  }
  
  private boolean isSameResolution(final IssueResolution it, final IssueResolution other) {
    return (((((it != null) && (other != null)) && Objects.equal(it.getDescription(), other.getDescription())) && Objects.equal(it.getLabel(), other.getLabel())) && 
      Objects.equal(it.getImage(), other.getImage()));
  }
  
  @Pure
  public IssueResolution getPrimaryResolution() {
    return this.primaryResolution;
  }
  
  public void setPrimaryResolution(final IssueResolution primaryResolution) {
    this.primaryResolution = primaryResolution;
  }
  
  @Pure
  public IMarker getPrimaryMarker() {
    return this.primaryMarker;
  }
  
  public void setPrimaryMarker(final IMarker primaryMarker) {
    this.primaryMarker = primaryMarker;
  }
}
