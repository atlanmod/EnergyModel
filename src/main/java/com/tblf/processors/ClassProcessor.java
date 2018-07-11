package com.tblf.processors;

import com.tblf.monitor.RAPLMonitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

public class ClassProcessor extends ClassVisitor {
    private String className;

    public ClassProcessor() {
        super(Opcodes.ASM5);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name.replace("/", ".");
        System.out.println("Visiting " + name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        return new AdviceAdapter(this.api, methodVisitor, access, name, desc) {
            @Override
            public void visitCode() {
                super.visitCode();
            }

            int value;

            @Override
            protected void onMethodEnter() {
                super.onMethodEnter();
                mv.visitTypeInsn(Opcodes.NEW, "com/tblf/monitor/RAPLMonitor");
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(className + "$" + name);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/tblf/monitor/RAPLMonitor", "<init>", "(Ljava/lang/String;)V", false);

                value = newLocal(Type.getType(RAPLMonitor.class));
                mv.visitVarInsn(Opcodes.ASTORE, value);
            }

            @Override
            protected void onMethodExit(int opcode) {

                //mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitVarInsn(Opcodes.ALOAD, value);
                mv.visitLdcInsn(className + "$" + name);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/tblf/monitor/RAPLMonitor", "report", "(Ljava/lang/String;)V", false);

                super.onMethodExit(opcode);
            }
        };
    }
}
