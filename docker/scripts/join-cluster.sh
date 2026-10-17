set -e

PRIMARY="rabbit@rabbit1"

rabbitmq-server -detached
rabbitmqctl await_startup

if rabbitmqctl cluster_status | grep -q "${PRIMARY}"; then
  echo "[join-cluster] rabbit2 ya es parte del clúster."
else
  echo "[join-cluster] Uniendo rabbit2 a ${PRIMARY}..."
  rabbitmqctl stop_app
  rabbitmqctl reset
  rabbitmqctl join_cluster "${PRIMARY}"
  rabbitmqctl start_app
fi

rabbitmqctl shutdown
exec rabbitmq-server
