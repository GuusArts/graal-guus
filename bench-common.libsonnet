{
  local common = import "common.jsonnet",
  local utils = import "common-utils.libsonnet",

  # benchmark job base with automatically generated name
  bench_base:: common.build_base + {
    # job name automatically generated: <job_prefix>-<suite>-<platform>-<jdk_version>-<os>-<arch>-<job_suffix>
    # null values are omitted from the list.
    generated_name:: utils.hyphenize([self.job_prefix, self.suite, self.platform, utils.prefixed_jdk(self.jdk_version), self.os, self.arch, self.job_suffix]),
    job_prefix:: null,
    job_suffix:: null,
    name:
      if self.is_jdk_supported(self.jdk_version) then self.generated_name
      else error "JDK" + self.jdk_version + " is not supported for " + self.generated_name + "! Suite is explicitly marked as working for JDK versions "+ self.min_jdk_version + " until " + self.max_jdk_version,
    suite:: error "'suite' must be set to generate job name",
    timelimit: error "build 'timelimit' is not set for "+ self.name +"!",
    local ol8_image = self.ci_resources.infra.ol8_bench_image,
    docker+: {
      "image": ol8_image,
      "mount_modules": true
    },
    should_use_hwloc:: std.objectHasAll(self, "is_numa") && self.is_numa && std.length(std.find("bench", self.targets)) > 0,
    min_jdk_version:: null,
    max_jdk_version:: null,
    is_jdk_supported(jdk_version)::
      if self.min_jdk_version != null && jdk_version < self.min_jdk_version then false
      else if self.max_jdk_version != null && jdk_version > self.max_jdk_version then false
      else true
  },

  # max number of threads to use for benchmarking in general
  # the goal being to limit parallelism on very large servers which may not be respresentative of real-world scenarios
  bench_max_threads:: {
   restrict_threads:: 36
  },

  bench_no_thread_cap:: {
    restrict_threads:: null,
    should_use_hwloc:: false
  },

  bench_hw:: {
    _bench_machine:: {
      targets+: ["bench"],
      machine_name:: error "machine_name must be set!",
      local _machine_name = self.machine_name,
      capabilities+: [_machine_name],
      local GR26994_ActiveProcessorCount = "-Dnative-image.benchmark.extra-run-arg=-XX:ActiveProcessorCount="+std.toString(self.threads_per_node), # remove once GR-26994 is fixed
      environment+: { "MACHINE_NAME": _machine_name, "GR26994": GR26994_ActiveProcessorCount },
      numa_nodes:: [],
      is_numa:: std.length(self.numa_nodes) > 0,
      num_threads:: error "num_threads must bet set!",
      hyperthreading:: true,
      threads_per_node:: if self.is_numa then self.num_threads / std.length(self.numa_nodes) else self.num_threads,
    },

    x52:: common.linux + common.amd64 + self._bench_machine + {
      machine_name:: "x52",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0,
      num_threads:: 72
    },
    e3:: common.linux + common.amd64 + self._bench_machine + {
      machine_name:: "e3",
      capabilities: ["e3", "tmpfs25g", "linux", "amd64"],
      numa_nodes:: [0, 1],
      default_numa_node:: 1,
      num_threads:: 256
    },
    e4_8_64:: common.linux + common.amd64 + self._bench_machine + {
      machine_name:: "e4_8_64",
      capabilities+: ["tmpfs25g"],
      numa_nodes:: [0],
      default_numa_node:: 0,
      num_threads:: 16
    },
    x82:: common.linux + common.amd64  + self._bench_machine + {
      machine_name:: "x82",
      capabilities+: ["tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0,
      num_threads:: 96
    },
    xgene3:: common.linux + common.aarch64  + self._bench_machine + {
      machine_name:: "xgene3",
      capabilities+: [],
      num_threads:: 32,
      hyperthreading:: false
    },
    a12c:: common.linux + common.aarch64 + self._bench_machine + {
      machine_name:: "a12c",
      capabilities+: ["no_frequency_scaling", "tmpfs25g"],
      numa_nodes:: [0, 1],
      default_numa_node:: 0,
      num_threads:: 160,
      hyperthreading:: false
    }
  },

  hwloc_cmd(cmd, num_threads, node, hyperthreading, max_threads_per_node)::
    if num_threads == null then
      ["hwloc-bind", "--cpubind", "node:"+node, "--membind", "node:"+node, "--"] + cmd
    else
      local threads = if num_threads != null then num_threads else max_threads_per_node;
      assert if hyperthreading then threads % 2 == 0 else true: "It is required to bind to an even number of threads on hyperthreaded machines. Got requested "+threads+" threads";
      assert threads <= max_threads_per_node: "Benchmarking must run on a single NUMA node for stability reasons. Got requested "+threads+" threads but the machine has only "+max_threads_per_node+" threads per node";      local cores = if hyperthreading then "0-"+((threads/2)-1)+".pu:0-1" else "0-"+(threads-1)+".pu:0";
      local cpu_bind = if hyperthreading then "node:"+node+".core:"+cores else "node:"+node+".core:"+cores+".pu:0";
      ["hwloc-bind", "--cpubind", cpu_bind, "--membind", "node:"+node, "--"] + cmd
  ,

  many_forks_benchmarking:: common.build_base + {
    // building block used to generate fork builds
    local benchmarking_config_repo = self.ci_resources.infra.benchmarking_config_repo,
    environment+: {
      BENCHMARKING_CONFIG_REPO: "$BUILD_DIR/benchmarking-config",
      FORK_COUNTS_DIRECTORY: "$BENCHMARKING_CONFIG_REPO/fork-counts",
      FORK_COUNT_FILE: error "FORK_COUNT_FILE env var must be set to use the many forks execution!"
    },
    setup+: [
      ["set-export", "CURRENT_BRANCH", ["git", "rev-parse", "--abbrev-ref", "HEAD"]],
      ["echo", "[BENCH-FORKS-CONFIG] Using configuration files from branch ${CURRENT_BRANCH} if it exists remotely."],
      ["git", "clone", benchmarking_config_repo, "${BENCHMARKING_CONFIG_REPO}"],
      ["test", "${CURRENT_BRANCH}", "=", "master", "||", "git", "-C", "${BENCHMARKING_CONFIG_REPO}", "checkout", "--track", "origin/${CURRENT_BRANCH}", "||", "echo", "Using default fork counts since there is no branch named '${CURRENT_BRANCH}' in the benchmarking-config repo."]
    ]
  },

  generate_fork_builds(suite_obj, subdir='compiler', forks_file_base_name=null)::
    /* based on a benchmark suite definition, generates the many forks version based on the hidden fields
     * 'forks_batches' that specifies the number of batches this job should be split into and the corresponding
     * 'forks_timelimit' that applies to those long-running jobs.
     *
     * The generated builder will set the 'FORK_COUNT_FILE' to the corresponding json file. So, make sure that the
     * mx benchmark command sets --fork-count-file=${FORK_COUNT_FILE}
     */

    if std.objectHasAll(suite_obj, "forks_batches") && std.objectHasAll(suite_obj, "forks_timelimit") && suite_obj.forks_batches != null then
      [ $.many_forks_benchmarking + suite_obj + {
        local batch_str = if suite_obj.forks_batches > 1 then "batch"+i else null,
        "job_prefix":: "bench-forks-" + subdir,
        "job_suffix":: batch_str,
        "timelimit": suite_obj.forks_timelimit,
        local base_name = if forks_file_base_name != null then forks_file_base_name else suite_obj.suite,
        "environment" +: {
          FORK_COUNT_FILE: "${FORK_COUNTS_DIRECTORY}/" + subdir + "/" + base_name + "_forks" + (if batch_str != null then "_"+batch_str else "") + ".json"
        }
      }
      for i in std.range(0, suite_obj.forks_batches - 1)]
    else
      [],
}
