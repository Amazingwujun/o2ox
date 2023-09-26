package com.jun.o2ox;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TransferAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        var project = e.getProject();
        var editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        var document = editor.getDocument();
        var psiFile = (PsiJavaFile) PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) {
            return;
        }
        var caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();
        var elementAt = psiFile.findElementAt(offset);
        var psiMethod = PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
        if (psiMethod == null) {
            Messages.showMessageDialog("Please put the cursor into method body", "O2OX", Messages.getWarningIcon());
            return;
        }
        var returnType = psiMethod.getReturnType();
        if (returnType == null || returnType.equals(PsiType.VOID)) {
            return;
        }
        var returnClass = PsiTypesUtil.getPsiClass(returnType);
        if (returnClass == null) {
            return;
        }
        var sourceClass = (PsiClass) psiMethod.getParent();
        if (sourceClass == null) {
            return;
        }

        // 在当前光标写入代买
        var statementStr = codeGen(returnClass, sourceClass);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.insertString(offset, statementStr);
            caretModel.moveToOffset(offset + statementStr.length());
            // 格式化代码
            PsiDocumentManager.getInstance(project).commitDocument(document);
            CodeStyleManager.getInstance(project).reformat(psiFile);
        });
    }

    /**
     * 判断转换方法的关联对象是否存在 lombok Accessors 注解
     *
     * @param returnClass 目标类型
     */
    private boolean hasAccessorsAnno(PsiClass returnClass) {
        for (var annotation : returnClass.getAnnotations()) {
            if (annotation.hasQualifiedName("lombok.experimental.Accessors")) {
                var chain = (PsiLiteralExpressionImpl) annotation.findAttributeValue("chain");
                return chain != null && Boolean.TRUE.equals(chain.getValue());
            }
        }
        return false;
    }

    private String codeGen(PsiClass target, PsiClass source) {
        if (hasAccessorsAnno(target)) {
            return codeGen1(target, source);
        } else {
            return codeGen2(target, source);
        }
    }

    private String codeGen1(PsiClass target, PsiClass source) {
        var sb = new StringBuilder();
        sb.append("return new ").append(target.getName()).append("()");
        // source 字段获取
        var psiFieldMap = Arrays.stream(source.getFields())
                .collect(Collectors.toMap(t -> t.getName().toLowerCase(), Function.identity()));
        for (var method : target.getMethods()) {
            var methodName = method.getName();
            if (methodName.startsWith("set")) {
                var mn = methodName.substring(3).toLowerCase();
                if (psiFieldMap.containsKey(mn)) {
                    var fieldName = psiFieldMap.get(mn).getName();
                    sb.append("\n.").append(methodName)
                            .append("(")
                            .append(fieldName)
                            .append(")");
                }
            }
        }
        sb.append(";");
        return sb.toString();
    }

    private String codeGen2(PsiClass target, PsiClass source) {
        var sb = new StringBuilder();
        sb.append("var result =").append(" new ").append(target.getName()).append("();");
        // source 字段获取
        var psiFieldMap = Arrays.stream(source.getFields())
                .collect(Collectors.toMap(t -> t.getName().toLowerCase(), Function.identity()));
        for (var method : target.getMethods()) {
            var methodName = method.getName();
            if (methodName.startsWith("set")) {
                var mn = methodName.substring(3).toLowerCase();
                if (psiFieldMap.containsKey(mn)) {
                    var fieldName = psiFieldMap.get(mn).getName();
                    sb
                            .append("\n")
                            .append("result.")
                            .append(methodName)
                            .append("(")
                            .append(fieldName)
                            .append(");");
                }
            }
        }
        sb.append("\nreturn result;");
        return sb.toString();
    }
}
