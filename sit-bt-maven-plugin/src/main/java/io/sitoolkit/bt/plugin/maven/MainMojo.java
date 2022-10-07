package io.sitoolkit.bt.plugin.maven;

import io.sitoolkit.bt.Main;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "translate")
public class MainMojo extends AbstractMojo {

  @Parameter(property = "bt.target")
  private String target;

  @Parameter(property = "bt.mode")
  private String mode;

  @Parameter(property = "bt.filePattern")
  private String filePattern;

  Main main = new Main();

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    List<String> args = new ArrayList<>();

    buildArgs(args, "--mode", mode);
    buildArgs(args, "--file-pattern", filePattern);

    if (target != null) {
      args.add(String.join(" ", target.split(",")));
    }

    main.execute(args.toArray(new String[args.size()]));
  }

  private void buildArgs(List<String> args, String name, String arg) {
    if (arg != null) {
      args.add(name);
      args.add(arg);
    }
  }
}
