# 命令创建 主题消息 Topic xfg-mq 你可以更换你需要的
docker exec -it rocketmq sh /home/app/rocketmq/bin/mqadmin updateTopic -n localhost:9876 -c DefaultCluster -t xfg-mq
