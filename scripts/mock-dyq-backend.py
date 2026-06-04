#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# PokeClaw DYQ后端Mock服务
# 用途：在真实后端未启动时，用于验证端侧设备API联调逻辑
# 作者：安卓小龙
# 日期：2026-05-18

import json
import os
import uuid
import time
from datetime import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

# 内存存储
devices = {}  # deviceId -> device_info
tasks = {}    # taskUuid -> task_info
tokens = {}   # deviceToken -> deviceId
experiences = []  # 端侧经验上报样本

# 模拟配置
MOUSE_DEVICE_TOKEN = "mock-device-token-" + str(uuid.uuid4())[:8]
MOUSE_REFRESH_TOKEN = "mock-refresh-token-" + str(uuid.uuid4())[:8]


def make_response(code: int = 200, msg: str = "success", data=None):
    """统一响应格式"""
    return jsonify({
        "code": code,
        "msg": msg,
        "data": data,
        "timestamp": int(time.time() * 1000)
    })


@app.route('/actuator/health', methods=['GET'])
def health_check():
    """健康检查端点"""
    return make_response(data={"status": "UP", "service": "dyq-mock"})


@app.route('/api/claw-device/register', methods=['POST'])
def device_register():
    """设备注册端点"""
    try:
        body = request.get_json(silent=True)
        if body is None:
            return make_response(code=400, msg="请求体无效：需要JSON格式")
        
        device_id = body.get('deviceId')
        if not device_id:
            return make_response(code=400, msg="缺少必填字段: deviceId")
        
        device_info = {
            "deviceId": device_id,
            "deviceName": body.get('deviceName', 'Mock Device'),
            "deviceModel": body.get('deviceModel', 'Unknown'),
            "androidVersion": body.get('androidVersion', '14'),
            "appVersion": body.get('appVersion', '0.7.0'),
            "registeredAt": datetime.now().isoformat(),
            "lastHeartbeatAt": None,
            "status": "ONLINE"
        }
        
        # 每个设备独立生成token
        dev_token = "mock-device-token-" + str(uuid.uuid4())[:8]
        refresh_token = "mock-refresh-token-" + str(uuid.uuid4())[:8]
        
        devices[device_id] = device_info
        devices[device_id]["deviceToken"] = dev_token
        devices[device_id]["refreshToken"] = refresh_token
        tokens[dev_token] = device_id
        tokens[refresh_token] = device_id
        
        # 为每个注册设备预置一个测试任务
        if device_id not in [t.get("deviceId") for t in tasks.values() if t["status"] == "PENDING"]:
            task_uuid = str(uuid.uuid4())
            tasks[task_uuid] = {
                "uuid": task_uuid,
                "taskUuid": task_uuid,
                "deviceId": device_id,
                "type": "SIMPLE_ACTION",
                "command": "打开设置查看电量",
                "mode": "TASK",
                "payload": {"action": "open_app", "packageName": "com.android.settings"},
                "status": "PENDING",
                "priority": "NORMAL",
                "createdAt": int(time.time() * 1000),
                "createdAtIso": datetime.now().isoformat()
            }
        
        print(f"[注册] 设备: {device_id}, 名称: {device_info['deviceName']}, token: {dev_token}")
        
        return make_response(data={
            "deviceToken": dev_token,
            "refreshToken": refresh_token,
            "expiresIn": 3600,
            "deviceId": device_id
        })
    except Exception as e:
        print(f"[注册错误] {e}")
        return make_response(code=500, msg=f"注册失败: {str(e)}")


@app.route('/api/claw-device/heartbeat', methods=['POST'])
def device_heartbeat():
    """设备心跳端点"""
    try:
        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            return make_response(code=401, msg="缺少有效令牌")
        
        token = auth_header[7:]
        device_id = tokens.get(token)
        
        if not device_id:
            return make_response(code=401, msg="令牌无效")
        
        body = request.get_json(silent=True) or {}
        
        # 更新设备状态
        if device_id in devices:
            devices[device_id]['lastHeartbeatAt'] = datetime.now().isoformat()
            devices[device_id]['batteryLevel'] = body.get('batteryLevel', 0)
            devices[device_id]['isCharging'] = body.get('isCharging', False)
            devices[device_id]['networkType'] = body.get('networkType', 'unknown')
        
        # 计算该设备的待处理任务数
        pending_count = len([t for t in tasks.values() if t['deviceId'] == device_id and t['status'] == 'PENDING'])
        
        print(f"[心跳] 设备: {device_id}, 电量: {body.get('batteryLevel')}%, 网络: {body.get('networkType')}")
        
        return make_response(data={
            "pendingTaskCount": pending_count,
            "serverTime": datetime.now().isoformat()
        })
    except Exception as e:
        print(f"[心跳错误] {e}")
        return make_response(code=500, msg=f"心跳失败: {str(e)}")


@app.route('/api/claw-device/devices/<device_id>/pending-tasks', methods=['GET'])
def get_pending_tasks(device_id):
    """拉取待处理任务"""
    try:
        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            return make_response(code=401, msg="缺少有效令牌")
        
        # 返回该设备的待处理任务
        device_tasks = [
            {
                # 端侧 PendingTaskItem 读取字段
                "taskUuid": t.get('taskUuid', t['uuid']),
                "command": t.get('command', '打开设置查看电量'),
                "mode": t.get('mode', 'TASK'),
                "priority": t.get('priority', 'NORMAL'),
                # 兼容旧 mock 字段，便于历史脚本继续读取
                "uuid": t['uuid'],
                "type": t['type'],
                "payload": t['payload'],
                "createdAt": t['createdAt']
            }
            for t in tasks.values()
            if t['deviceId'] == device_id and t['status'] == 'PENDING'
        ]
        
        print(f"[拉取任务] 设备: {device_id}, 任务数: {len(device_tasks)}")
        
        return make_response(data=device_tasks)
    except Exception as e:
        print(f"[拉取任务错误] {e}")
        return make_response(code=500, msg=f"拉取任务失败: {str(e)}")


@app.route('/api/claw-device/tasks/<task_uuid>/result', methods=['POST'])
def submit_task_result(task_uuid):
    """提交任务结果"""
    try:
        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            return make_response(code=401, msg="缺少有效令牌")
        
        body = request.get_json() or {}
        
        if task_uuid in tasks:
            tasks[task_uuid]['status'] = body.get('status', 'UNKNOWN')
            tasks[task_uuid]['result'] = body.get('result')
            tasks[task_uuid]['completedAt'] = datetime.now().isoformat()
        
        print(f"[上报结果] 任务: {task_uuid}, 状态: {body.get('status')}, 结果: {body.get('result', 'N/A')[:50]}")
        
        return make_response(data={"taskUuid": task_uuid, "received": True})
    except Exception as e:
        print(f"[上报结果错误] {e}")
        return make_response(code=500, msg=f"上报结果失败: {str(e)}")


@app.route('/api/claw-device/experiences/report', methods=['POST'])
def report_experience():
    """提交端侧经验样本，用于无真机替代证据包。"""
    try:
        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            return make_response(code=401, msg="缺少有效令牌")

        token = auth_header[7:]
        device_id = tokens.get(token)
        if not device_id:
            return make_response(code=401, msg="令牌无效")

        body = request.get_json() or {}
        experience_id = "exp-" + str(uuid.uuid4())[:8]
        record = {
            "experienceId": experience_id,
            "deviceId": device_id,
            "taskUuid": body.get("taskUuid"),
            "lessonType": body.get("lessonType", "TASK_EXECUTION"),
            "outcome": body.get("outcome", "SUCCESS"),
            "summary": body.get("summary"),
            "metrics": body.get("metrics", {}),
            "evidenceRefs": body.get("evidenceRefs", []),
            "reportedAt": datetime.now().isoformat()
        }
        experiences.append(record)

        print(f"[经验上报] 设备: {device_id}, 经验: {experience_id}, 任务: {record['taskUuid']}")

        return make_response(data={
            "experienceId": experience_id,
            "received": True,
            "deviceId": device_id,
            "taskUuid": record["taskUuid"]
        })
    except Exception as e:
        print(f"[经验上报错误] {e}")
        return make_response(code=500, msg=f"经验上报失败: {str(e)}")


@app.route('/api/claw-device/token/refresh', methods=['POST'])
def refresh_token():
    """刷新令牌"""
    try:
        body = request.get_json() or {}
        old_refresh = body.get('refreshToken')
        
        # 简化逻辑：查找旧refreshToken对应的设备，注册新token
        old_token = body.get('deviceToken', '')
        device_id = tokens.get(old_token) or tokens.get(old_refresh)
        
        new_token = "mock-device-token-" + str(uuid.uuid4())[:8]
        new_refresh = "mock-refresh-token-" + str(uuid.uuid4())[:8]
        
        # 注册新令牌到映射
        if device_id:
            tokens[new_token] = device_id
        
        print(f"[刷新令牌] 设备={device_id}, 新令牌已注册")
        
        return make_response(data={
            "deviceToken": new_token,
            "refreshToken": new_refresh,
            "expiresIn": 3600
        })
    except Exception as e:
        print(f"[刷新令牌错误] {e}")
        return make_response(code=500, msg=f"刷新令牌失败: {str(e)}")


@app.route('/api/status', methods=['GET'])
def get_status():
    """获取Mock服务状态"""
    return jsonify({
        "devices": len(devices),
        "tasks": len(tasks),
        "experiences": len(experiences),
        "device_list": list(devices.keys())
    })


if __name__ == '__main__':
    port = int(os.environ.get('MOCK_PORT', '18080'))
    print("=" * 60)
    print("PokeClaw DYQ后端Mock服务")
    print("=" * 60)
    print("端点列表:")
    print("  POST /api/claw-device/register       - 设备注册")
    print("  POST /api/claw-device/heartbeat      - 设备心跳")
    print("  GET  /api/claw-device/devices/{id}/pending-tasks - 拉取任务")
    print("  POST /api/claw-device/tasks/{uuid}/result - 上报结果")
    print("  POST /api/claw-device/experiences/report - 经验上报样本")
    print("  POST /api/claw-device/token/refresh  - 刷新令牌")
    print("  GET  /actuator/health                - 健康检查")
    print("  GET  /api/status                     - Mock状态")
    print("=" * 60)
    print(f"监听地址: http://0.0.0.0:{port}")
    print("=" * 60)
    
    app.run(host='0.0.0.0', port=port, debug=False)
