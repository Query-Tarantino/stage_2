#!/usr/bin/env bash

readonly MODES=(hazelcast activemq activemq-persistent polling orchestrator)

compose_files() {
    local mode=$1 override
    case $mode in
        hazelcast) override="" ;;
        activemq) override="-f docker-compose.activemq.yml" ;;
        activemq-persistent) override="-f docker-compose.activemq.yml -f docker-compose.activemq-persistent.yml" ;;
        polling) override="-f docker-compose.polling.yml" ;;
        orchestrator) override="-f docker-compose.orchestrator.yml" ;;
        *)
            echo "Unknown coordination mode $mode, expected one of ${MODES[*]}" >&2
            return 2
            ;;
    esac
    echo "-f docker-compose.yml -f docker-compose.mirror.yml $override"
}

broker_of_mode() {
    case $1 in
        hazelcast) echo hazelcast ;;
        activemq | activemq-persistent) echo activemq ;;
        *) echo "" ;;
    esac
}

export_profiles_of_mode() {
    if [[ $(broker_of_mode "$1") == activemq ]]; then
        export COMPOSE_PROFILES=activemq
    else
        unset COMPOSE_PROFILES
    fi
}
