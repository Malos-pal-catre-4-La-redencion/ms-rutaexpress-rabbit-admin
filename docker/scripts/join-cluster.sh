#!/bin/bash
# Une rabbit2 al clúster de rabbit1. Es idempotente: si el nodo ya pertenece
# al clúster (por ejemplo tras un reinicio), solo lo deja en primer plano.
set -e

PRIMARY="rabbit@rabbit1"

# 1) Arrancar el nodo en segundo plano y esperar a que responda.
rabbitmq-server -detached
rabbitmqctl await_startup

# 2) Unirse solo si todavía no forma parte del clúster.
if rabbitmqctl cluster_status | grep -q "${PRIMARY}"; then
  echo "[join-cluster] rabbit2 ya es parte del clúster."
else
  echo "[join-cluster] Uniendo rabbit2 a ${PRIMARY}..."
  rabbitmqctl stop_app
  rabbitmqctl reset
  rabbitmqctl join_cluster "${PRIMARY}"
  rabbitmqctl start_app
fi

# 3) Detener el nodo en segundo plano y relanzarlo en primer plano para que
#    Docker vea el proceso principal y registre sus logs.
rabbitmqctl shutdown
exec rabbitmq-server
