execution_type="none"
server_name="Remotely"
auto_console_join=true
jar_file="server.jar"
min_memory="4G"
max_memory="4G"
server_port="default"
force_chunks_upgrade=false
server_type="Bukkit"
auto_restart="false"
restart_time=3
# It's recommended to use "worlds" to orginize your worlds in a folder -RedxAx
worlds_folder="default"
worlds_main_name="default"
ignore_java_version="false"
java_start_command="java"
profiling=false
fix_java_12_issues=true
debug_agent_address="disable"
log4j_config="default"
aikar_bukkit_jvm_flags="-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+AlwaysPreTouch -XX:InitiatingHeapOccupancyPercent=15 -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseAES -XX:+UseAESIntrinsics -XX:+UseFMA -XX:AllocatePrefetchStyle=1 -XX:+UseLoopPredicate -XX:+RangeCheckElimination -XX:+EliminateLocks -XX:+DoEscapeAnalysis -XX:+UseCodeCacheFlushing -XX:+SegmentedCodeCache -XX:+UseFastJNIAccessors -XX:+OptimizeStringConcat -XX:+UseCompressedOops -XX:+TrustFinalNonStaticFields -XX:+UseInlineCaches -XX:+RewriteBytecodes -XX:+RewriteFrequentPairs -XX:+UseNUMA -XX:+UseFPUForSpilling -XX:+UseNewLongLShift -XX:+UseVectorCmov -XX:+ScavengeBeforeFullGC -XX:+OptimizeFill -Dusing.aikars.flags=https://mcflags.emc.gs/  -Daikars.new.flags=true -XX:+UseNUMA -XX:+UseFastUnorderedTimeStamps"
aikar_proxy_jvm_flags="-XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -XX:MaxInlineLevel=15"
bukkit_jvm_flags="-Dfile.encoding=UTF-8 -Dlog4j2.formatMsgNoLookups=true -Dnet.kyori.ansi.colorLevel=indexed256"
proxy_jvm_flags="-Dfile.encoding=UTF-8 -Dlog4j2.formatMsgNoLookups=true -Dnet.kyori.ansi.colorLevel=indexed256"
bukkit_app_flags="--nogui"
proxy_app_flags=""
display_start_command=true
process_hint="${server_name}"
script_name=$(basename "$0")

if [ "$jar_file" = "auto" ]; then
	for file in *.jar; do
		[ -f "$file" ] || break
		if [ "$jar_file" != "auto" ]; then
			echo ""
			echo "Multiple jar-files found: $jar_file and $file. Please specify jar_file option in $script_name"
			echo ""
			exit
		fi
		jar_file=$file
	done
	if [ "$jar_file" = "auto" ]; then
		echo ""
		echo "Jar-file not found. Please specify jar_file option in $script_name"
		echo ""
		exit
	fi
	echo "Using jar-file $jar_file"
fi

if [ "$1" != "deep" ]; then
	if [ "$execution_type" = "screen" ]; then
		screen -A -m -d -S ${server_name} bash "${script_name}" deep
		[ "$auto_console_join" = true ] && sleep 0.2 && screen -x ${server_name}
	elif [ "$execution_type" = "tmux" ]; then
		tmux new -d -s ${server_name}
		sleep 0.2
		tmux send-keys -t ${server_name} "bash \"${script_name}\" deep" Enter
		[ "$auto_console_join" = true ] && sleep 0.2 && tmux attach -t ${server_name}
	elif [ "$execution_type" = "none" ]; then
		echo "Server will start in a couple of minutes"
		sleep 2.0
		bash "${script_name}" deep
	else
		echo ""
		echo "Wrong execution_type: $execution_type"
		echo ""
	fi
	exit
fi

#!/bin/bash

if [ "$java_start_command" != "deep" ]; then
    if [ "$java_start_command" != "deep" ]; then
        start_command="${java_start_command}"
    else
		echo ""
		echo "Wrong start_command: $execution_type"
		echo ""
    fi
fi


if [ "$worlds_folder" != "deep" ]; then
    if [ "$server_type" = "Proxy" ]; then
        [ "$worlds_folder" != "default" ] && proxy_app_flags+=""
        [ "$worlds_folder" = "default" ] && proxy_app_flags="proxy_app_flags"
    elif [ "$server_type" = "Bukkit" ]; then
        [ "$worlds_folder" != "default" ] && bukkit_app_flags+=" --world-dir ${worlds_folder}"
        [ "$worlds_folder" = "default" ] && bukkit_app_flags="$bukkit_app_flags"
    else
        echo ""
        echo " Sorry but your server_type is incorrect!"
        echo " It will be taken from bukkit.yml"
        echo ""
        [ "$worlds_folder" = "deep" ] && proxy_app_flags+=""
        [ "$worlds_folder" = "deep" ] && bukkit_app_flags+=""
    fi
fi

if [ "$worlds_main_name" != "deep" ]; then
    if [ "$server_type" = "Proxy" ]; then
        [ "$worlds_main_name" != "default" ] && proxy_app_flags+=""
        [ "$worlds_main_name" = "default" ] && proxy_app_flags="proxy_app_flags"
    elif [ "$server_type" = "Bukkit" ]; then
        [ "$worlds_main_name" != "default" ] && bukkit_app_flags+=" -w ${worlds_main_name}"
        [ "$worlds_main_name" = "default" ] && bukkit_app_flags="$bukkit_app_flags"
    else
        echo ""
        echo " Sorry but you didn't select the main name for your worlds!"
        echo " The name will be taken from server.properties"
        echo ""
    fi
fi


bukkit_jvm_flags+=" ${aikar_bukkit_jvm_flags}"
proxy_jvm_flags+=" ${aikar_proxy_jvm_flags}"

if [ "$profiling" = true ]; then
	process_hint+="-profiling"
	bukkit_jvm_flags="${bukkit_jvm_flags// -XX:+PerfDisableSharedMem/}"
	bukkit_jvm_flags+=" -Xshare:off"
	proxy_jvm_flags="${proxy_jvm_flags// -XX:+PerfDisableSharedMem/}"
	proxy_jvm_flags+=" -Xshare:off"
fi

process_hint="$USER.${process_hint}"

add_opens_packages=()
add_modules_list=(
	"jdk.incubator.vector"
)

if [ "$fix_java_12_issues" = true ]; then
	add_opens_packages+=(
		"java.base/java.lang"
		"java.base/java.lang.reflect"
		"java.base/java.lang.invoke"
		"java.base/java.security"
	)
fi

for package in "${add_opens_packages[@]}"; do
	bukkit_jvm_flags+=" --add-opens ${package}=ALL-UNNAMED"
	proxy_jvm_flags+=" --add-opens ${package}=ALL-UNNAMED"
done

for module_index in "${!add_modules_list[@]}"; do
	if [ "$module_index" = 0 ]; then
		bukkit_jvm_flags+=" --add-modules="
		proxy_jvm_flags+=" --add-modules="
	else
		bukkit_jvm_flags+=","
		proxy_jvm_flags+=","
	fi
	bukkit_jvm_flags+="${add_modules_list[$module_index]}"
	proxy_jvm_flags+="${add_modules_list[$module_index]}"
done

[ "$debug_agent_address" != "disable" ] && bukkit_jvm_flags+=" -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${debug_agent_address}"
[ "$debug_agent_address" != "disable" ] && proxy_jvm_flags+=" -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${debug_agent_address}"

[ "$log4j_config" != "default" ] && bukkit_jvm_flags+=" -Dlog4j.configurationFile=${log4j_config}"
[ "$log4j_config" != "default" ] && proxy_jvm_flags+=" -Dlog4j.configurationFile=${log4j_config}"

[ "$ignore_java_version" = "true" ] && bukkit_jvm_flags+=" -DPaper.IgnoreJavaVersion=true"
[ "$ignore_java_version" = "true" ] && proxy_jvm_flags+=" -DPaper.IgnoreJavaVersion=true"

[ "$server_port" != "default" ] && bukkit_app_flags+=" -port ${server_port}"
[ "$server_port" != "default" ] && proxy_app_flags+=" -port ${server_port}"

[ "$force_chunks_upgrade" = true ] && bukkit_app_flags+=" --forceUpgrade"


bukkit_start_command="${start_command} -D_server=${process_hint} -Xms${min_memory} -Xmx${max_memory} ${bukkit_jvm_flags} -jar ${jar_file} ${bukkit_app_flags}"
proxy_start_command="${start_command} -D_server=${process_hint} -Xms${min_memory} -Xmx${max_memory} ${proxy_jvm_flags} -jar ${jar_file} ${proxy_app_flags}"

if [ "$display_start_command" = true ]; then
	echo ""
	echo " Start command: $start_command"
	echo ""
fi

add="true" 
if [ "$add" = true ]; then
	echo "| Remotely"
fi

if [ "$display_start_command" = true ]; then
    echo ""
    if [ "$server_type" = "Bukkit" ]; then
        echo " Start command: $bukkit_start_command"
		echo ""
    elif [ "$server_type" = "Proxy" ]; then
        echo " Start command: $proxy_start_command"
		echo ""
    else
        echo ""
        echo " Unknown server_type: $server_type. Unable to display start command."
        echo ""
    fi
    echo ""
fi


while true; do
    if [ "$server_type" = "Bukkit" ]; then
        echo ""
        eval " ${bukkit_start_command}"
        echo ""
    elif [ "$server_type" = "Proxy" ]; then
        echo ""
        eval " ${proxy_start_command}"
        echo ""
    else
        echo ""
        echo " Unknown server_type: $server_type please select Proxy or Bukkit in start.sh"
        echo ""
        continue
    fi

    if [ "$auto_restart" = "false" ]; then
	break	
	fi	
done
