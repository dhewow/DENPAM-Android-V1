import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import com.sun.source.util.JavacTask;

/** Parse-only: checks source syntax without requiring the Android SDK. */
public final class JavaParserCheck {
  public static void main(String[] args) throws Exception {
    JavaCompiler c=ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
    try(StandardJavaFileManager f=c.getStandardFileManager(errors,null,null)){
      Iterable<? extends JavaFileObject> files=f.getJavaFileObjectsFromStrings(Arrays.asList(args));
      JavacTask task=(JavacTask)c.getTask(null,f,errors, List.of("-proc:none"),null,files);
      task.parse();
    }
    long count=errors.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
    errors.getDiagnostics().forEach(System.out::println);
    if(count>0)System.exit(1);
    System.out.println("Java source syntax: OK ("+args.length+" file)");
  }
}
