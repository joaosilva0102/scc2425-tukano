#!/bin/sh
artillery run register_users.yaml
artillery run create_shorts.yaml
artillery run realistic_flow.yaml --record --key a9_5tGEp4AXf9E1xyZ6Z83DZLSQKu4Ezk0y --name 'Realistic Flow | No Cache | 10 | Remote | West US'
artillery run user_delete.yaml