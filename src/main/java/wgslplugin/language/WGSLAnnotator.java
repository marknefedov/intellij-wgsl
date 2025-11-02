package wgslplugin.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wgslplugin.language.psi.*;
import wgslplugin.language.psi.impl.WGSLPsiImplUtil;
import wgslplugin.language.psi.impl.WGSLStructFieldImpl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class WGSLAnnotator implements Annotator {

    public static final Set<String> FRAGMENT_SHADER_ONLY_BUILTINS = Set.of(
            "dpdx", "dpdxCoarse", "dpdxFine", "dpdy", "dpdyCoarse", "dpdyFine", "fwidth", "fwidthCoarse", "fwidthFine"
    );

    public static final Set<String> ATTRIBUTE_NAMES = Set.of(
            "align", "binding", "builtin", "const", "group", "id", "interpolate", "invariant", "location", "size", "workgroup_size"
    );

    public static final Set<String> DEPRECATED_ATTRIBUTE_NAMES = Set.of(
            "stage"
    );

    public static final Set<String> STAGE_NAMES = Set.of(
            "compute", "fragment", "vertex"
    );

  public static final Set<String> RESERVED_KEYWORDS = Set.of(
      "NULL", "Self", "abstract", "active", "alignas", "alignof", "as", "asm",
      "asm_fragment", "async", "attribute", "auto", "await", "become", "cast",
      "catch", "class", "co_await", "co_return", "co_yield", "coherent",
      "column_major", "common", "compile", "compile_fragment", "concept",
      "const_cast", "consteval", "constexpr", "constinit", "crate", "debugger",
      "decltype", "delete", "demote", "demote_to_helper", "do", "dynamic_cast",
      "enum", "explicit", "export", "extends", "extern", "external",
      "fallthrough", "filter", "final", "finally", "friend", "from", "fxgroup",
      "get", "goto", "groupshared", "highp", "impl", "implements", "import",
      "inline", "instanceof", "interface", "layout", "lowp", "macro",
      "macro_rules", "match", "mediump", "meta", "mod", "module", "move", "mut",
      "mutable", "namespace", "new", "nil", "noexcept", "noinline",
      "nointerpolation", "non_coherent", "noncoherent", "noperspective", "null",
      "nullptr", "of", "operator", "package", "packoffset", "partition", "pass",
      "patch", "pixelfragment", "precise", "precision", "premerge", "priv",
      "protected", "pub", "public", "readonly", "ref", "regardless", "register",
      "reinterpret_cast", "require", "resource", "restrict", "self", "set",
      "shared", "sizeof", "smooth", "snorm", "static", "static_assert",
      "static_cast", "std", "subroutine", "super", "target", "template", "this",
      "thread_local", "throw", "trait", "try", "type", "typedef", "typeid",
      "typename", "typeof", "union", "unless", "unorm", "unsafe", "unsized",
      "use", "using", "varying", "virtual", "volatile", "wgsl", "where", "with",
      "writeonly", "yield");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {

        if(element instanceof WGSLNamedElement) {
            var named = (WGSLNamedElement) element;
            if (RESERVED_KEYWORDS.contains(named.getName())) {
                holder.newAnnotation(HighlightSeverity.ERROR, "'" + named.getName() + "' is a reserved keyword").range(element).create();
            }
        }

        if(element instanceof WGSLFuncCallStatement) {
            var fragment_shader = false;

            @Nullable ASTNode call_name = element.getNode().findChildByType(WGSLTypes.FUNC_CALL_NAME);
            if(call_name != null) {
                @Nullable ASTNode name = call_name.findChildByType(WGSLTypes.IDENT);

                // Get the parent all the way to the function declaration
                var parent = element.getParent();
                while (parent != null && !(parent instanceof WGSLFunctionDecl)) {
                    parent = parent.getParent();
                }

                if (name != null) {
                    String txt = name.getText();

                    if (FRAGMENT_SHADER_ONLY_BUILTINS.contains(txt)) {
                        // If we have a parent that was a function declaration we get the attribute list and
                        // check if it contains the attribute 'stage(fragment)'
                        if (parent != null) {
                            var attributes = ((WGSLFunctionDecl) parent).getAttributeList();
                            if (attributes != null) {
                                for (var i : attributes.getAttributeList()) {
                                    String t = i.getText();
                                    fragment_shader |= (t.equals("fragment") || t.equals("stage(fragment)"));
                                }
                            }
                        }

                        if (fragment_shader) {
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(name).textAttributes(WGSLColours.BUILTIN_FUNCTION.attributes()).create();
                        } else {
                            holder.newAnnotation(HighlightSeverity.ERROR, "This built-in is only allowed in fragment shader functions").range(name).create();
                        }
                    } else if (BuiltInFunctions.INSTANCE.get(name.getPsi()) != null) {
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(name).textAttributes(WGSLColours.BUILTIN_FUNCTION.attributes()).create();
                    } else {
                    }
                }
            }
        } else if(element instanceof WGSLFunctionName) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(WGSLColours.FUNCTION_NAME.attributes()).create();
        } else if(element instanceof WGSLFieldIdent) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(WGSLColours.FIELD.attributes()).create();
        } else if(element instanceof WGSLAttributeName) {
            String name = WGSLPsiImplUtil.getName(element);
            if(ATTRIBUTE_NAMES.contains(name) || STAGE_NAMES.contains(name)) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(WGSLColours.ATTRIBUTE.attributes()).create();
                // TODO: implement validations for attribute values
            } else {
                if(DEPRECATED_ATTRIBUTE_NAMES.contains(name)) {
                    if(annotationEnabled(element, "old-attributes")) {
                        holder.newAnnotation(HighlightSeverity.WARNING, "Deprecated attribute").range(element).create();
                    }
                } else {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Unknown attribute").range(element).create();
                }
            }
        } else if(element instanceof WGSLStructMember) {
            PsiElement e = element.getLastChild();
            if(e != null) {
                @NotNull IElementType et = e.getNode().getElementType();
                if (et == WGSLTypes.SEMICOLON) {
                    if(annotationEnabled(element, "old-struct-syntax")) {
                        holder.newAnnotation(HighlightSeverity.WARNING, "Semicolon is deprecated, replace with comma").range(e).create();
                    }
                }

                PsiElement sib = element.getNextSibling();
                while(sib != null && sib instanceof PsiWhiteSpace) {
                    sib = sib.getNextSibling();
                }
                if(sib instanceof WGSLStructMember) {
                    // handle the case where there is no comma between struct members
                    if(et != WGSLTypes.SEMICOLON && et != WGSLTypes.COMMA) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Comma expected").range(element).create();
                    }
                }
            }
        } else if(element instanceof WGSLAttributeList) {
            if(element.getFirstChild().getNode().getElementType() == WGSLTypes.ATTR_LEFT) {
                if(annotationEnabled(element, "old-attribute-syntax")) {
                    holder.newAnnotation(HighlightSeverity.WARNING, "Deprecated attribute syntax").range(element).create();
                }
            }
        } else if(element instanceof WGSLGlobalConstantDecl) {
            for(ASTNode node : element.getNode().getChildren(null)) {
                if(node.getElementType() == WGSLTypes.LET) {
                    if(annotationEnabled(element, "old-global-constant-decl")) {
                        holder.newAnnotation(HighlightSeverity.WARNING, "Deprecated global constant syntax").range(element).create();
                    }
                    break;
                }
            }
        }
    }

    private boolean annotationEnabled(PsiElement element, String name) {
        PsiFile file = element.getContainingFile();
        if(file != null) {
            PsiElement first = file.getFirstChild();
            if(first != null) {
                ASTNode node = first.getNode();
                if (node.getElementType() == WGSLTypes.LINE_COMMENT) {
                    String txt = node.getText();
                    if(txt != null && txt.startsWith("//+")) {
                        return !txt.contains(name);
                    }
                }
            }
        }
        return true;
    }
}
