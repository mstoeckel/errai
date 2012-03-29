package org.jboss.errai.codegen.tests;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.jboss.errai.codegen.Context;
import org.jboss.errai.codegen.SnapshotMaker;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.builder.BlockBuilder;
import org.jboss.errai.codegen.builder.ClassStructureBuilder;
import org.jboss.errai.codegen.builder.impl.ClassBuilder;
import org.jboss.errai.codegen.exception.CyclicalObjectGraphException;
import org.jboss.errai.codegen.tests.model.Person;
import org.jboss.errai.codegen.tests.model.PersonImpl;
import org.jboss.errai.codegen.tests.model.SnapshotInterfaceWithCollections;
import org.jboss.errai.codegen.tests.model.SnapshotInterfaceWithCollectionsImpl;
import org.jboss.errai.codegen.util.Stmt;
import org.junit.Assert;
import org.junit.Test;

// Note: extends from AbstractCodeGenTest to inherit overridden assertEquals(String, String) methods which are whitespace
//       insensitive.
public class SnapshotMakerTest extends AbstractCodegenTest {

  @Test
  public void testGenerateSnapshotOfMethod() throws Exception {
    Person mother = new PersonImpl("mom", 30, null);
    Person child = new PersonImpl("kid", 5, mother);
    Statement snapshotStmt = SnapshotMaker.makeSnapshotAsSubclass(child, Person.class, null, Person.class);

    final String generated
            = snapshotStmt.generate(Context.create());

    final String expectedValue =
            "new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "  public int getAge() {\n" +
                    "    return 5;\n" +
                    "  }\n" +
                    "  public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "    return new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "      public int getAge() {\n" +
                    "        return 30;\n" +
                    "      }\n" +
                    "      public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "        return null;\n" +
                    "      }\n" +
                    "      public String getName() {\n" +
                    "        return \"mom\";\n" +
                    "      }\n" +
                    "    };\n" +
                    "  }\n" +
                    "  public String getName() {\n" +
                    "    return \"kid\";\n" +
                    "  }\n" +
                    "}";

    assertEquals(expectedValue, generated);

  }

  @Test
  public void testNoStackOverflowOnObjectCycle() {
    PersonImpl cycle1 = new PersonImpl("cycle1", 30, null);
    Person cycle2 = new PersonImpl("cycle2", 5, cycle1);
    cycle1.setMother(cycle2);

    try {
      Statement snapshotStmt = SnapshotMaker.makeSnapshotAsSubclass(cycle2, Person.class, null, Person.class);
      System.out.println(snapshotStmt.generate(Context.create()));
      Assert.fail("Instance cycle was not detected");
    }
    catch (CyclicalObjectGraphException e) {
      assertTrue(e.getObjectsInvolvedInCycle().contains(cycle1));
      assertTrue(e.getObjectsInvolvedInCycle().contains(cycle2));
    }
  }

  /**
   * This test confirms the SnapshotMaker is called automatically by the code generator's literal reification logic
   * for interfaces and classes explicitly market "literalizable" in the context.
   *
   * @throws Exception
   */
  @Test
  public void testCollectionsLiteralizableInSnapshots() throws Exception {
    Person jonathan = new PersonImpl("Jonathan F.", 20, new PersonImpl("Jonathans's Parent", 50, null));
    Person christian = new PersonImpl("Christian S.", 20, new PersonImpl("Christians's Parent", 50, null));
    Person mike = new PersonImpl("Mike B.", 0, new PersonImpl("Mike's Parent", 50, null));

    SnapshotInterfaceWithCollections personCollection
            = new SnapshotInterfaceWithCollectionsImpl(Arrays.asList(jonathan, christian, mike));

    // create a manual context
    final Context ctx = Context.create();

    // tell the code generator that classes that implement these interfaces are literalizable
    ctx.addLiteralizableClass(SnapshotInterfaceWithCollections.class);
    ctx.addLiteralizableClass(Person.class);

    // initialize a new builder with this context and try and build 'snapshot' as a code snapshot
    final String generated = Stmt.create(ctx).load(personCollection).toJavaString();

    final String expectedValue =
            "new org.jboss.errai.codegen.tests.model.SnapshotInterfaceWithCollections() {\n" +
                    "  public java.util.List getPersons() {\n" +
                    "    return new java.util.ArrayList() {\n" +
                    "      {\n" +
                    "        add(new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "          public int getAge() {\n" +
                    "            return 20;\n" +
                    "          }\n" +
                    "          public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "            return new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "              public int getAge() {\n" +
                    "                return 50;\n" +
                    "              }\n" +
                    "              public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "                return null;\n" +
                    "              }\n" +
                    "              public String getName() {\n" +
                    "                return \"Jonathans's Parent\";\n" +
                    "              }\n" +
                    "            };\n" +
                    "          }\n" +
                    "          public String getName() {\n" +
                    "            return \"Jonathan F.\";\n" +
                    "          }\n" +
                    "        });\n" +
                    "        add(new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "          public int getAge() {\n" +
                    "            return 20;\n" +
                    "          }\n" +
                    "          public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "            return new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "              public int getAge() {\n" +
                    "                return 50;\n" +
                    "              }\n" +
                    "              public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "                return null;\n" +
                    "              }\n" +
                    "              public String getName() {\n" +
                    "                return \"Christians's Parent\";\n" +
                    "              }\n" +
                    "            };\n" +
                    "          }\n" +
                    "          public String getName() {\n" +
                    "            return \"Christian S.\";\n" +
                    "          }\n" +
                    "        });\n" +
                    "        add(new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "          public int getAge() {\n" +
                    "            return 0;\n" +
                    "          }\n" +
                    "          public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "            return new org.jboss.errai.codegen.tests.model.Person() {\n" +
                    "              public int getAge() {\n" +
                    "                return 50;\n" +
                    "              }\n" +
                    "              public org.jboss.errai.codegen.tests.model.Person getMother() {\n" +
                    "                return null;\n" +
                    "              }\n" +
                    "              public String getName() {\n" +
                    "                return \"Mike's Parent\";\n" +
                    "              }\n" +
                    "            };\n" +
                    "          }\n" +
                    "          public String getName() {\n" +
                    "            return \"Mike B.\";\n" +
                    "          }\n" +
                    "        });\n" +
                    "      }\n" +
                    "    };\n" +
                    "  }\n" +
                    "}\n";

    assertEquals(expectedValue, generated);
  }

  @Test
  public void testCannedRepresentation() {
    Person mom = new PersonImpl("mom", 30, null);
    Person kid1 = new PersonImpl("Kid 1", 3, mom);
    Person kid2 = new PersonImpl("Kid 2", 4, mom);
    Person kid3 = new PersonImpl("Kid 3", 5, mom);

    ClassStructureBuilder<?> peopleClassBuilder = ClassBuilder.define("com.foo.People").publicScope().body();
    BlockBuilder<?> method = peopleClassBuilder.publicMethod(void.class, "makePeople").body();

    Statement momVar = Stmt
        .declareVariable(Person.class)
        .asFinal()
        .named("mom")
        .initializeWith(SnapshotMaker.makeSnapshotAsSubclass(mom, Person.class, null, Person.class));
    method.append(momVar);

    Map<Object, Statement> momReference = Collections.singletonMap((Object) mom, Stmt.loadVariable("mom").returnValue());

    method.append(Stmt.declareVariable("kid1", SnapshotMaker.makeSnapshotAsSubclass(kid1, Person.class, momReference, Person.class)));
    method.append(Stmt.declareVariable("kid2", SnapshotMaker.makeSnapshotAsSubclass(kid2, Person.class, momReference, Person.class)));
    method.append(Stmt.declareVariable("kid3", SnapshotMaker.makeSnapshotAsSubclass(kid3, Person.class, momReference, Person.class)));

    method.finish();

    final String generated = peopleClassBuilder.toJavaString();

    String expected = "package com.foo;\n" +
            "\n" +
            "import org.jboss.errai.codegen.tests.model.Person;\n" +
            "\n" +
            "public class People {\n" +
            "  public void makePeople() {\n" +
            "    final Person mom = new Person() {\n" +
            "      public int getAge() {\n" +
            "        return 30;\n" +
            "      }\n" +
            "      public Person getMother() {\n" +
            "        return null;\n" +
            "      }\n" +
            "      public String getName() {\n" +
            "        return \"mom\";\n" +
            "      }\n" +
            "    };\n" +
            "    Person kid1 = new Person() {\n" +
            "      public int getAge() {\n" +
            "        return 3;\n" +
            "      }\n" +
            "      public Person getMother() {\n" +
            "        return mom;\n" +
            "      }\n" +
            "      public String getName() {\n" +
            "        return \"Kid 1\";\n" +
            "      }\n" +
            "    };\n" +
            "    Person kid2 = new Person() {\n" +
            "      public int getAge() {\n" +
            "        return 4;\n" +
            "      }\n" +
            "      public Person getMother() {\n" +
            "        return mom;\n" +
            "      }\n" +
            "      public String getName() {\n" +
            "        return \"Kid 2\";\n" +
            "      }\n" +
            "    };\n" +
            "    Person kid3 = new Person() {\n" +
            "      public int getAge() {\n" +
            "        return 5;\n" +
            "      }\n" +
            "      public Person getMother() {\n" +
            "        return mom;\n" +
            "      }\n" +
            "      public String getName() {\n" +
            "        return \"Kid 3\";\n" +
            "      }\n" +
            "    };\n" +
            "  }\n" +
            "}\n";
    assertEquals(expected, generated);
  }
}
