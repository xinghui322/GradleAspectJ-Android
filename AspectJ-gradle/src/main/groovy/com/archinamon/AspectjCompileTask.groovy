package com.archinamon

import com.android.build.gradle.api.BaseVariant
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile

import static com.archinamon.AndroidAspectJPlugin.getAjPath
import static com.archinamon.AndroidAspectJPlugin.getVariants

class AspectjCompileTask extends AbstractCompile {

    private String logFile;
    private String encoding;

    private boolean binaryWeave;
    private boolean weaveInfo;
    private boolean addSerialVUID;
    private boolean ignoreErrors;

    private boolean interruptOnWarnings;
    private boolean interruptOnErrors;
    private boolean interruptOnFails;

    private FileCollection aspectPath;
    private String bootClasspath;
    def private binaryWeavePath = [];

    @Override
    @TaskAction
    protected void compile() {

        final def log = project.logger

        //http://www.eclipse.org/aspectj/doc/released/devguide/ajc-ref.html
        //
        // -sourceroots:
        //  Find and build all .java or .aj source files under any directory listed in DirPaths. DirPaths, like classpath, is a single argument containing a list of paths to directories, delimited by the platform- specific classpath delimiter. Required by -incremental.
        // -inpath:
        //  Accept as source bytecode any .class files in the .jar files or directories on Path. The output will include these classes, possibly as woven with any applicable aspects. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -classpath:
        //  Specify where to find user class files. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -aspectpath:
        //  Weave binary aspects from jar files and directories on path into all sources. The aspects should have been output by the same version of the compiler. When running the output classes, the run classpath should contain all aspectPath entries. Path, like classpath, is a single argument containing a list of paths to jar files, delimited by the platform- specific classpath delimiter.
        // -bootclasspath:
        //  Override location of VM's bootClasspath for purposes of evaluating types when compiling. Path is a single argument containing a list of paths to zip files or directories, delimited by the platform-specific path delimiter.
        // -d:
        //  Specify where to place generated .class files. If not specified, Directory defaults to the current working dir.
        // -preserveAllLocals:
        //  Preserve all local variables during code generation (to facilitate debugging).

        def args = [
                "-encoding", getEncoding(),
                "-source", getSourceCompatibility(),
                "-target", getTargetCompatibility(),
                "-d", destinationDir.absolutePath,
                "-classpath", classpath.asPath,
                "-bootclasspath", bootClasspath,
                "-sourceroots", sourceRoots.join(File.pathSeparator)
        ];

        if (getLogFile() != null) {
            args << "-log" << getLogFile();
        }

        if (getBinaryWeave()) {
            args << "-inpath" << getBinaryWeavePath().join(File.pathSeparator);
        }

        if (getWeaveInfo()) {
            args << "-showWeaveInfo";
        }

        if (getAddSerialVUID()) {
            args << "-XaddSerialVersionUID";
        }

        if (getIgnoreErrors()) {
            args << "-proceedOnError" << "-noImportError";
        }

        if (!aspectPath.isEmpty()) {
            args << "-aspectpath" << getAspectPath().asPath;
        }

        log.debug "ajc args: " + Arrays.toString(args as String[]);

        MessageHandler handler = new MessageHandler(true);
        new Main().run(args as String[], handler);
        for (IMessage message : handler.getMessages(null, true)) {
            switch (message.getKind()) {
                case IMessage.ERROR:
                    log.error message.message, message.thrown
                    if (getInterruptOnErrors()) throw new StopExecutionException(message.message);
                    break;
                case IMessage.FAIL:
                case IMessage.ABORT:
                    log.error message.message, message.thrown
                    throw new StopExecutionException(message.message);
                case IMessage.INFO:
                case IMessage.DEBUG:
                case IMessage.WARNING:
                    log.warn message.message, message.thrown
                    if (getInterruptOnWarnings()) throw new StopExecutionException(message.message);
                    break;
            }
        }
    }

    @Input
    String getLogFile() {
        return logFile
    }

    void setLogFile(String name) {
        if (name != null && name.length() > 0)
            this.logFile = project.buildDir.absolutePath + File.separator + name;
    }

    @Input
    String getEncoding() {
        return encoding
    }

    void setEncoding(String encoding) {
        this.encoding = encoding
    }

    @Input
    boolean getBinaryWeave() {
        return binaryWeave;
    }

    void setBinaryWeave(boolean val) {
        this.binaryWeave = val;
    }

    @Input
    boolean getWeaveInfo() {
        return weaveInfo;
    }

    void setWeaveInfo(boolean val) {
        this.weaveInfo = val;
    }

    @Input
    boolean getIgnoreErrors() {
        return ignoreErrors;
    }

    void setIgnoreErrors(boolean val) {
        this.ignoreErrors = val;
    }

    @Input
    boolean getAddSerialVUID() {
        return addSerialVUID;
    }

    void setAddSerialVUID(boolean val) {
        this.addSerialVUID = val;
    }

    @Input
    boolean getInterruptOnWarnings() {
        return interruptOnWarnings;
    }

    void setInterruptOnWarnings(boolean val) {
        this.interruptOnWarnings = val;
    }

    void setInterruptOnFails(boolean val) {
        this.interruptOnFails = val;
    }

    @Input
    boolean getInterruptOnErrors() {
        return interruptOnErrors;
    }

    void setInterruptOnErrors(boolean val) {
        this.interruptOnErrors = val;
    }

    @InputFiles
    FileCollection getAspectPath() {
        return aspectPath
    }

    void setAspectPath(FileCollection aspectpath) {
        this.aspectPath = aspectpath
    }

    @Input
    String getBootClasspath() {
        return bootClasspath
    }

    void setBootClasspath(String bootclasspath) {
        this.bootClasspath = bootclasspath
    }

    @InputFiles
    def getBinaryWeavePath() {
        return binaryWeavePath
    }

    void setBinaryWeavePath(String inpath) {
        if (new File(inpath).exists())
            this.binaryWeavePath << inpath;
    }

    File[] getSourceRoots() {
        def sourceRoots = []
        source.sourceCollections.each {
            it.asFileTrees.each {
                if ((it.dir as File).exists()) sourceRoots << it.dir;
            }
        }

        // preserve all buildTypes and flavors
        getVariants(project).all { BaseVariant variant ->
            final def Closure applier = { String name ->
                File dir = getFile(project, name);
                if (dir.exists() && !sourceRoots.contains(dir)) sourceRoots << dir;
            }
            variant.productFlavors*.name.each(applier);
            variant.buildType*.name.each(applier);
        }

        return sourceRoots;
    }

    def static File getFile(Project project, String path) {
        return new File(project.projectDir.absolutePath + File.separator + getAjPath(path));
    }
}
