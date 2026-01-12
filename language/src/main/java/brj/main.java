package brj;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Entry point for running bridje applications.
 * 
 * Usage: java -cp ... brj.main -m my:namespace arg1 arg2
 * 
 * This class creates a GraalVM Context, loads the specified namespace,
 * and invokes its 'main' function with the provided arguments.
 */
public class main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java brj.main -m <namespace> [args...]");
            System.err.println("  -m <namespace>  Namespace containing main function (e.g., my:app)");
            System.exit(1);
        }
        
        if (!"-m".equals(args[0])) {
            System.err.println("Error: First argument must be -m");
            System.err.println("Usage: java brj.main -m <namespace> [args...]");
            System.exit(1);
        }
        
        String namespace = args[1];
        
        // Collect remaining arguments for the main function
        String[] mainArgs = new String[args.length - 2];
        System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
        
        try (Context context = Context.newBuilder()
                .allowAllAccess(true)
                .logHandler(System.err)
                .build()) {
            
            context.enter();
            try {
                // Load the namespace by evaluating a require statement in a temporary namespace
                String loadCode = String.format(
                    "ns: __brj_main_loader\n" +
                    "  require:\n" +
                    "    %s\n",
                    formatNamespaceForRequire(namespace)
                );
                
                context.eval("bridje", loadCode);
                
                // Get the namespace from bindings
                Value bindings = context.getBindings("bridje");
                if (!bindings.hasMember(namespace)) {
                    System.err.println("Error: Namespace '" + namespace + "' not found");
                    System.exit(1);
                }
                
                Value ns = bindings.getMember(namespace);
                
                // Check if the namespace has a 'main' function
                if (!ns.hasMember("main")) {
                    System.err.println("Error: Namespace '" + namespace + "' does not have a 'main' function");
                    System.exit(1);
                }
                
                Value mainFn = ns.getMember("main");
                if (!mainFn.canExecute()) {
                    System.err.println("Error: 'main' in namespace '" + namespace + "' is not a function");
                    System.exit(1);
                }
                
                // Convert args to a Bridje vector by evaluating a vector literal
                Value argsVector = createVector(context, mainArgs);
                
                // Invoke the main function
                mainFn.execute(argsVector);
                
            } finally {
                context.leave();
            }
            
        } catch (PolyglotException e) {
            if (e.isExit()) {
                System.exit(e.getExitStatus());
            }
            System.err.println("Error executing main function:");
            e.printStackTrace(System.err);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
    
    /**
     * Format namespace for require block.
     * Converts "foo:bar" to "foo:\n      bar"
     */
    private static String formatNamespaceForRequire(String namespace) {
        int colonIndex = namespace.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid namespace format: " + namespace);
        }
        
        String prefix = namespace.substring(0, colonIndex);
        String suffix = namespace.substring(colonIndex + 1);
        
        return prefix + ":\n      " + suffix;
    }
    
    /**
     * Create a Bridje vector from Java String array
     */
    private static Value createVector(Context context, String[] args) {
        StringBuilder vectorCode = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                vectorCode.append(", ");
            }
            // Escape the string for Bridje
            vectorCode.append('"');
            vectorCode.append(escapeString(args[i]));
            vectorCode.append('"');
        }
        vectorCode.append("]");
        
        return context.eval("bridje", vectorCode.toString());
    }
    
    /**
     * Escape a string for use in Bridje code
     */
    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
