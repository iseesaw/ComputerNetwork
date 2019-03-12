# -*- coding: utf-8 -*-
from socket import *
import os
import tkinter as tk
from tkinter import *
from tkinter.filedialog import askopenfilename
import threading
import time

class Application(tk.Frame):
    def __init__(self, master=None):
        super().__init__(master)
        self.master = master
        self.pack()

        self.start()

    def start(self):
        self.btn_udp = tk.Button(self, text='Start UDP Server', command=lambda: self.thread_it(self.udp_recv_file))
        self.btn_udp.grid(row=0, column=0)
        self.info_udp = tk.Label(self, text='Listen at 10240')
        self.info_udp.grid(row=0, column=1)

        self.btn_tcp = tk.Button(self, text='Start TCP Server', command=lambda: self.thread_it(self.tcp_recv_file))
        self.btn_tcp.grid(row=1, column=0)
        self.info_tdp = tk.Label(self, text='Listen at 10241')
        self.info_tdp.grid(row=1, column=1)

        self.btn_all = tk.Button(self, text='Start All', command=self.recv_start)
        self.btn_all.grid(row=2, column=0)

    def recv_start(self):
        threading.Thread(target=self.udp_recv_file).start()
        threading.Thread(target=self.tcp_recv_file).start()

    def thread_it(self, func):
        t = threading.Thread(target=func)
        # 子线程随主线程结束
        t.setDaemon(True)
        t.start()
        # 主线程被子线程阻塞
        # t.join()

    def udp_recv_file(self):
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
            #while recv_size < file_size:
            while True:
                try:
                    data, client_addr = server_socket.recvfrom(BATCH_SIZE)
                    f.write(data)
                    #recv_size += len(data)
                except:
                    break

        print('UDP: Receive All Successfully!')
        server_socket.close()

    def tcp_recv_file(self):
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
    root = tk.Tk()
    root.resizable(width=False, height=False)
    root.geometry('%dx%d+%d+%d' % (300, 100, 600, 300))
    app = Application(master=root)
    app.mainloop()
