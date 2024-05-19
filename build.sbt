inThisBuild {
  val scala3 = "3.4.2"

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
    semanticdbEnabled      := true,
    versionScheme          := Some("early-semver"),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeProfileName    := "io.kaizen-solutions",
    sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
    licenses               := List("MPL-2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0/")),
    organization           := "io.kaizen-solutions",
    organizationName       := "kaizen-solutions",
    homepage               := Some(url("https://www.kaizen-solutions.io/")),
    developers := List(
      Developer("calvinlfer", "Calvin Fernandes", "cal@kaizen-solutions.io", url("https://www.kaizen-solutions.io"))
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org"
  )
}

lazy val root =
  project
    .in(file("."))
    .settings(
      publish / skip := true,
      name           := "virgil-kyo",
      libraryDependencies ++= Seq(
        "io.getkyo"           %% "kyo-core"    % "0.10.0",
        "io.kaizen-solutions" %% "virgil-core" % "1.1.0"
      )
    )
