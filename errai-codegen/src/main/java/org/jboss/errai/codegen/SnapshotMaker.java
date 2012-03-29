package org.jboss.errai.codegen;

import static org.jboss.errai.codegen.util.PrettyPrinter.prettyPrintJava;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.errai.codegen.builder.AnonymousClassStructureBuilder;
import org.jboss.errai.codegen.builder.impl.ObjectBuilder;
import org.jboss.errai.codegen.exception.CyclicalObjectGraphException;
import org.jboss.errai.codegen.exception.GenerationException;
import org.jboss.errai.codegen.exception.NotLiteralizableException;
import org.jboss.errai.codegen.literal.NullLiteral;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaClassFactory;
import org.jboss.errai.codegen.meta.MetaMethod;
import org.jboss.errai.codegen.util.Stmt;

import com.google.gwt.dev.util.collect.IdentityHashSet;

/**
 * Utility class for creating code-generated snapshots of certain types of live value objects.
 * The classes and interfaces that SnapshotMaker works with have the following characteristics:
 * <ul>
 *  <li>It must be an interface or a non-final class with a public no-args constructor.
 *  <li>None of the public methods take arguments (except {@code equals(Object)}, which is always ignored)
 *  <li>Each public method must return one of the following:
 *    <ul>
 *      <li>{@code void}
 *      <li>a Java primitive type
 *      <li>a {@link Context#addLiteralizableClass(Class) literalizable type} in the current code generator context
 *      <li>a type that is explicitly mentioned as a "type to recurse on" (these types must in turn follow this set of rules)
 *      <li>an object instance for which a "canned representation" is provided (these are matched by instance equality, not by type)
 *    </ul>
 *  </ul>
 *
 * @author Jonathan Fuerth <jfuerth@gmail.com>
 * @author Mike Brock
 */
public final class SnapshotMaker {

  /** This class should not be instantiated. */
  private SnapshotMaker() {}

  /**
   * Code-generates an object whose methods return (snapshots of) the same
   * values as the given object.
   *
   * @param o
   *          The object to snapshot.
   * @param typeToExtend
   *          The type that the snapshot will have. This type must meet the
   *          requirements listed in the SnapshotMaker class-level
   *          documentation.
   * @param cannedRepresentations
   *          A map of objects for which you want to provide a pre-made return
   *          statement, rather than allowing the code generator's default
   *          behaviour to generate a snapshot. This can be used to refer back
   *          to an existing variable or a singleton instance.
   *          <p>
   *          The provided statements will be used verbatim as the entire method
   *          body of any method that returns the corresponding key value, so be
   *          sure that each statement is a return statement.
   * @param typesToRecurseOn
   *          The types for which the snapshot maker should be applied
   *          recursively.
   * @return A Statement representing the value of the object
   * @throws CyclicalObjectGraphException
   *           if any objects reachable from {@code o} form a reference cycle.
   *           The simplest example of this would be a method on {@code o} that
   *           returns {@code o} itself. You may be able to work around such a
   *           problem by supplying a canned representation of one of the
   *           objects in the cycle.
   */
  public static Statement makeSnapshotAsSubclass(
      final Object o,
      final Class<?> typeToExtend,
      final Map<Object, Statement> cannedRepresentations,
      final Class<?> ... typesToRecurseOn) {
    MetaClass metaTypeToExtend = MetaClassFactory.get(typeToExtend);
    MetaClass[] metaTypesToRecurseOn = new MetaClass[typesToRecurseOn.length];
    for (int i = 0; i < typesToRecurseOn.length; i++) {
      metaTypesToRecurseOn[i] = MetaClassFactory.get(typesToRecurseOn[i]);
    }
    return makeSnapshotAsSubclass(o, metaTypeToExtend, cannedRepresentations, metaTypesToRecurseOn);
  }

  /**
   * Code-generates an object whose methods return (snapshots of) the same
   * values as the given object.
   *
   * @param o
   *          The object to snapshot.
   * @param typeToExtend
   *          The type that the snapshot will have. This type must meet the
   *          requirements listed in the SnapshotMaker class-level
   *          documentation.
   * @param cannedRepresentations
   *          A map of objects for which you want to provide a pre-made
   *          Statement, rather than allowing the code generator's default
   *          behaviour to generate a snapshot. This can be used to refer back
   *          to an existing variable or a singleton instance.
   * @param typesToRecurseOn
   *          The types for which the snapshot maker should be applied
   *          recursively.
   * @return A Statement representing the value of the object
   * @throws CyclicalObjectGraphException
   *           if any objects reachable from {@code o} form a reference cycle.
   *           The simplest example of this would be a method on {@code o} that
   *           returns {@code o} itself. You may be able to work around such a
   *           problem by supplying a canned representation of one of the objects
   *           in the cycle.
   */
  public static Statement makeSnapshotAsSubclass(
      final Object o,
      final MetaClass typeToExtend,
      final Map<Object, Statement> cannedRepresentations,
      final MetaClass ... typesToRecurseOn) {

    IdentityHashMap<Object, Statement> existingSnapshots = new IdentityHashMap<Object, Statement>();
    if (cannedRepresentations != null) {
      existingSnapshots.putAll(cannedRepresentations);
    }

    return makeSnapshotAsSubclass(
        o,
        typeToExtend,
        new HashSet<MetaClass>(Arrays.asList(typesToRecurseOn)),
        existingSnapshots,
        new IdentityHashSet<Object>());
  }

  /**
   * Implementation for the same-named public methods.
   *
   * @param o
   *          The object to snapshot.
   * @param typeToExtend
   *          The type of the snapshot to produce.
   * @param typesToRecurseOn
   *          Types for which this method should be called recursively.
   * @param cannedRepresentations
   *          Object instances for which a given representation should be used.
   * @param existingSnapshots
   *          Object instances for which a snapshot has already been completed.
   *          Bootstrap this with an empty IdentityHashMap.
   * @param unfinishedSnapshots
   *          Object instances for which a partially-completed snapshot exists.
   *          If one of these objects is returned by a method in {@code o}, this
   *          causes a CyclicalObjectGraphException.
   * @return A Statement of type {@code typeToExtend} that represents the
   *         current publicly visible state of {@code o}.
   */
  private static Statement makeSnapshotAsSubclass(
      final Object o,
      final MetaClass typeToExtend,
      final Set<MetaClass> typesToRecurseOn,
      final IdentityHashMap<Object, Statement> existingSnapshots,
      final IdentityHashSet<Object> unfinishedSnapshots) {

    if (o == null) {
      return NullLiteral.INSTANCE;
    }

    if (!typeToExtend.isAssignableFrom(o.getClass())) {
      throw new IllegalArgumentException(
          "Given object (of type " + o.getClass().getName() +
              ") is not an instance of requested type " + typeToExtend.getName());
    }

    System.out.println("** Making snapshot of " + o);
    System.out.println("   Existing snapshots: " + existingSnapshots);

    final List<MetaMethod> sortedMethods = Arrays.asList(typeToExtend.getMethods());
    Collections.sort(sortedMethods, new Comparator<MetaMethod>() {
      @Override
      public int compare(MetaMethod m1, MetaMethod m2) {
        return m1.getName().compareTo(m2.getName());
      }
    });

    Iterator<MetaMethod> it = sortedMethods.iterator();
    while (it.hasNext()) {
      MetaMethod m = it.next();
      if ("equals".equals(m.getName()) || "hashCode".equals(m.getName())) {
        it.remove();
        continue;
      }
      if (m.getParameters().length > 0) {
        throw new UnsupportedOperationException("I can't make a snapshot of a type that has public methods with parameters (other than equals()).");
      }
    }

    System.out.println("   Creating a new statement");
    return new Statement() {
      String generatedCache;

      /**
       * We retain a mapping of return values to the methods that returned them,
       * in case we need to provide diagnostic information when an exception is
       * thrown.
       */
      IdentityHashMap<Object, MetaMethod> methodReturnVals = new IdentityHashMap<Object, MetaMethod>();

      @Override
      public String generate(Context context) {
        System.out.println("++ Statement.generate() for " + o);
        if (generatedCache != null) return generatedCache;

        // create a subcontext and record the types we will allow the LiteralFactory to create automatic
        // snapshots for.
        final Context subContext = Context.create(context);
        subContext.addLiteralizableMetaClasses(typesToRecurseOn);

        final AnonymousClassStructureBuilder builder = ObjectBuilder.newInstanceOf(typeToExtend, context)
            .extend();
        unfinishedSnapshots.add(o);
        for (MetaMethod method : sortedMethods) {
          System.out.println("  method " + method.getName());
          System.out.println("    return type " + method.getReturnType());
          if (method.getReturnType().equals(void.class)) {
            builder.publicOverridesMethod(method.getName()).finish();
            System.out.println("  finished method " + method.getName());
            continue;
          }
          try {

            final Object retval = typeToExtend.asClass().getMethod(method.getName()).invoke(o);
            methodReturnVals.put(retval, method);
            System.out.println("    retval=" + retval);
            Statement methodBody;
            if (existingSnapshots.containsKey(retval)) {
              System.out.println("    using existing snapshot");
              methodBody = existingSnapshots.get(retval);
            }
            else if (subContext.isLiteralizableClass(method.getReturnType().getErased())) {
              if (unfinishedSnapshots.contains(retval)) {
                throw new CyclicalObjectGraphException(unfinishedSnapshots);
              }

              // use Stmt.create(context) to pass the context along.
              System.out.println("    >> recursing for " + retval);
              methodBody = Stmt.create(subContext).nestedCall(makeSnapshotAsSubclass(
                  retval, method.getReturnType(), typesToRecurseOn, existingSnapshots, unfinishedSnapshots)).returnValue();
            }
            else {
              System.out.println("    relying on literal factory");
              methodBody = Stmt.load(retval).returnValue();
            }

            System.out.println("  finished method " + method.getName());

            builder.publicOverridesMethod(method.getName()).append(methodBody).finish();
            existingSnapshots.put(retval, methodBody);
          }
          catch (GenerationException e) {
            e.appendFailureInfo("In attempt to snapshot return value of "
                + typeToExtend.getFullyQualifiedName() + "." + method.getName() + "()");
            throw e;
          }
          catch (RuntimeException e) {
            throw e;
          }
          catch (Exception e) {
            throw new GenerationException("Failed to extract value for snapshot", e);
          }
        }

        System.out.println("    finished: " + builder);

        try {
          generatedCache = prettyPrintJava(builder.finish().toJavaString());
        } catch (NotLiteralizableException e) {
          MetaMethod m = methodReturnVals.get(e.getNonLiteralizableObject());
          if (m != null) {
            e.appendFailureInfo("This value came from method " +
                  m.getDeclaringClass().getFullyQualifiedNameWithTypeParms() + "." + m.getName() +
                  ", which has return type " + m.getReturnType());
          }
          throw e;
        } catch (GenerationException e) {
          e.appendFailureInfo("While generating a snapshot of " + o.toString() +
              " (actual type: " + o.getClass().getName() +
              "; type to extend: " + typeToExtend.getFullyQualifiedName() + ")");
          throw e;
        }
        unfinishedSnapshots.remove(o);
        return generatedCache;
      }

      @Override
      public MetaClass getType() {
        return typeToExtend;
      }
    };
  }

}
