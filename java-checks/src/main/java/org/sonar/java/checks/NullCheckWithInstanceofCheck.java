/*
 * SonarQube Java
 * Copyright (C) 2012-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.java.model.ExpressionUtils;
import org.sonar.java.model.SyntacticEquivalence;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.UnaryExpressionTree;

@Rule(key = "S4201")
public class NullCheckWithInstanceofCheck extends IssuableSubscriptionVisitor {

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.CONDITIONAL_AND, Tree.Kind.CONDITIONAL_OR);
  }

  @Override
  public void visitNode(Tree tree) {
    if (!hasSemantic()) {
      return;
    }
    BinaryExpressionTree binaryExpression = (BinaryExpressionTree) tree;
    ExpressionTree leftNoParenth = ExpressionUtils.skipParentheses(binaryExpression.leftOperand());
    ExpressionTree rightNoParenth = ExpressionUtils.skipParentheses(binaryExpression.rightOperand());

    if (((leftNoParenth.is(Tree.Kind.EQUAL_TO) || rightNoParenth.is(Tree.Kind.EQUAL_TO))
      && (expressionMatches(binaryExpression, leftNoParenth, rightNoParenth, Tree.Kind.CONDITIONAL_OR))) ||
      ((leftNoParenth.is(Tree.Kind.NOT_EQUAL_TO) || rightNoParenth.is(Tree.Kind.NOT_EQUAL_TO))
        && (expressionMatches(binaryExpression, leftNoParenth, rightNoParenth, Tree.Kind.CONDITIONAL_AND)))) {
      reportIssue(treeToReport(leftNoParenth, rightNoParenth), "Remove this unnecessary null check; \"instanceof\" returns false for nulls.");
    }
  }

  private static boolean expressionMatches(BinaryExpressionTree binaryExpression, ExpressionTree leftNoParenth, ExpressionTree rightNoParenth, Tree.Kind kind) {
    ExpressionTree instanceofVariable = null;
    ExpressionTree binaryVariable = Optional.ofNullable(binaryExpressionVariable(leftNoParenth))
      .orElse(binaryExpressionVariable(rightNoParenth));
    if (binaryVariable == null || !binaryExpression.is(kind)) {
      return false;
    } else {
      instanceofVariable = Optional.ofNullable(instanceofFound(rightNoParenth, kind))
        .orElse(instanceofFound(leftNoParenth, kind));
      if (instanceofVariable != null && SyntacticEquivalence.areEquivalent(binaryVariable, instanceofVariable)) {
        return true;
      }
    }
    return false;
  }

  private static ExpressionTree treeToReport(ExpressionTree left, ExpressionTree right) {
    return left.is(Tree.Kind.EQUAL_TO, Tree.Kind.NOT_EQUAL_TO) ? left : right;
  }

  private static ExpressionTree binaryExpressionVariable(ExpressionTree expression) {
    BinaryExpressionTree binaryExpression = null;
    if (expression.is(Tree.Kind.NOT_EQUAL_TO, Tree.Kind.EQUAL_TO)) {
      binaryExpression = (BinaryExpressionTree) expression;
      if (binaryExpression.leftOperand().is(Tree.Kind.NULL_LITERAL)) {
        return binaryExpression.rightOperand();
      } else if (binaryExpression.rightOperand().is(Tree.Kind.NULL_LITERAL)) {
        return binaryExpression.leftOperand();
      }
    }
    return null;
  }

  private static ExpressionTree instanceofFound(ExpressionTree expressionTree, Tree.Kind kind) {
    if (Tree.Kind.CONDITIONAL_OR.equals(kind)) {
      /* if CONDITIONAL_OR we want LOGICAL COMPLEMENT before instanceof */
      if (expressionTree.is(Tree.Kind.LOGICAL_COMPLEMENT)) {
        return instanceofLHS(ExpressionUtils.skipParentheses(((UnaryExpressionTree) expressionTree).expression()));
      } else {
        return null;
      }
    } else {
      return instanceofLHS(expressionTree);
    }
  }

  private static ExpressionTree instanceofLHS(ExpressionTree expressionTree) {
    if (expressionTree.is(Tree.Kind.INSTANCE_OF)) {
      return ((InstanceOfTree) expressionTree).expression();
    }
    return null;
  }
}
