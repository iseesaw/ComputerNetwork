# -*- coding: utf-8 -*-
from socket import *
import threading

def udp_recv_file():
    server_port = 10240
    server_socket = socket(AF_INET, SOCK_DGRAM)
    server_socket.bind(('', server_port))

    print('UDP: Ready to receive file...')
    BATCH_SIZE = 1024
    # 获取文件大小
    #data, client_addr = server_socket.recvfrom(BATCH_SIZE)
    #file_size = int(data)

    with open('udp_recv.zip', 'wb') as f:
        # 写入文件
        data, client_addr = server_socket.recvfrom(BATCH_SIZE)
        f.write(data)
        # 开始接受文件
        server_socket.settimeout(5)
        recv_size = 0
        # while recv_size < file_size:
        while True:
            try:
                data, client_addr = server_socket.recvfrom(BATCH_SIZE)
                f.write(data)
                # recv_size += len(data)
            except:
                break

    print('UDP: Receive All Successfully!')
    server_socket.close()

def tcp_recv_file():
    # 服务器监听端口
    server_port = 10241
    BATCH_SIZE = 1024
    # 　服务器主套接字
    server_socket = socket(AF_INET, SOCK_STREAM)

    # 绑定端口
    server_socket.bind(('', server_port))
    # 监听
    server_socket.listen(1)
    print('TCP: Ready to receive file...')

    # 服务器欢迎套接字
    connection_socket, addr = server_socket.accept()
    # 获得文件大小
    data = connection_socket.recv(BATCH_SIZE)
    file_size = int(data)

    # 开始接受文件
    recv_size = 0
    with open('tcp_recv.zip', 'wb') as f:
        # 接受文件并写入
        while recv_size < file_size:
            # print(recv_size, file_size)
            data = connection_socket.recv(BATCH_SIZE)
            f.write(data)
            recv_size += len(data)

    print('TCP: Receive All Successfully!')
    connection_socket.close()
    server_socket.close()

if __name__ == '__main__':
    threading.Thread(target=tcp_recv_file).start()
    threading.Thread(target=udp_recv_file).start()