/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.intentions.PyAnnotateTypesIntention;
import com.jetbrains.python.inspections.quickfix.PyQuickFixUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyMissingTypeHintsInspection extends PyInspection{
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new PyElementVisitor() {
      @Override
      public void visitPyFunction(PyFunction function) {
        if (!(typeCommentExists(function) || typeAnnotationsExist(function))) {
          ASTNode nameNode = function.getNameNode();
          if (nameNode != null) {
            holder.registerProblem(nameNode.getPsi(), "Type hinting is missing for function definition", new AddTypeHintsQuickFix(function.getName()));
          }
        }
      }
    };
  }

  private static boolean typeCommentExists(PyFunction function) {
    ASTNode node = function.getStatementList().getNode().getFirstChildNode();
    
    while (node != null && node.getElementType() != PyTokenTypes.END_OF_LINE_COMMENT) {
      node = node.getTreeNext();
    }
    if (node != null) {
      return isTypeComment(node);
    }
    
    node = function.getStatementList().getPrevSibling().getNode();
    
    while (node != null && node.getElementType() != PyTokenTypes.COLON && node.getElementType() != PyTokenTypes.END_OF_LINE_COMMENT) {
      node = node.getTreePrev();
    }
    
    if (node != null && node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      return isTypeComment(node);
    }
    
    return false;
  }

  private static boolean isTypeComment(ASTNode node) {
    String commentText = node.getText();
    int startInd = commentText.indexOf('#');
    if (startInd != -1) {
      commentText = commentText.substring(startInd+1).trim();
      if (commentText.startsWith("type:")) {
        return true;
      }
    }
    return false;
  }

  private static boolean typeAnnotationsExist(PyFunction function) {
    for (PyParameter param: function.getParameterList().getParameters()) {
      PyNamedParameter namedParameter = param.getAsNamed();
      if (namedParameter != null) {
        if (namedParameter.getAnnotation() != null) {
          return true;
        }
      }
    }
    if (function.getAnnotation() != null) {
      return true;
    }
    
    return false;
  }

  private static class AddTypeHintsQuickFix implements LocalQuickFix {
    private String myName;

    public AddTypeHintsQuickFix(@NotNull String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Add type hinting for '" + myName + "'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Add type hinting";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyFunction function = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);
      
      if (function != null) {
        PyAnnotateTypesIntention.annotateTypes(PyQuickFixUtil.getEditor(function), function);
      }
    }
  }
}
