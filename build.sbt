inThisBuild {
  val scala3 = "3.7.1"

  List(
    scalaVersion       := scala3,
    crossScalaVersions := Seq(scala3),
    scalacOptions ++= Seq(
      "-encoding",
      "utf8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wconf:msg=(discarded.*value|pure.*statement):error",
      "-Wunused:all",
      "-language:implicitConversions"
    ),
    githubWorkflowJavaVersions := List(
      JavaSpec.temurin("17"),
      JavaSpec.temurin("21")
    ),
    githubWorkflowBuild := Seq(
      WorkflowStep.Sbt(
        name = Option("Build & Test"),
        commands = List("clean", "test"),
        cond = None,
        env = Map.empty
      )
    ),
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches := Seq(
      RefPredicate.StartsWith(Ref.Tag("v")),
      RefPredicate.Equals(Ref.Branch("main"))
    ),
    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        commands = List("ci-release"),
        name = Some("Publish project"),
        env = Map(
          "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
        )
      )
    ),
    testFrameworks ++= Seq(),
    semanticdbEnabled := true,
    versionScheme     := Some("early-semver"),
    licenses          := List("MPL-2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0/")),
    organization      := "io.kaizen-solutions",
    organizationName  := "kaizen-solutions",
    homepage          := Some(url("https://www.kaizen-solutions.io/")),
    developers := List(
      Developer("calvinlfer", "Calvin Fernandes", "cal@kaizen-solutions.io", url("https://www.kaizen-solutions.io"))
    )
  )
}

lazy val root =
  project
    .in(file("."))
    .settings(
      name := "virgil-kyo",
      libraryDependencies ++= Seq(
        "io.getkyo"           %% "kyo-core"    % "1.0-RC1",
        "io.kaizen-solutions" %% "virgil-core" % "1.2.3"
      )
    )
