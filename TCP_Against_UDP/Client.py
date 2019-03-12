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

        # 输入文件位置和IP地址
        self.file_path = StringVar()
        self.file_path.set('data.zip')
        self.ip_addr = StringVar()
        self.ip_addr.set('68.168.137.7')

        # 选择文件
        self.choose_file()
        # 输入IP地址
        self.input_ipaddr()
        # 启动UDP、TCP
        self.start()

    # 选择文件
    def choose_file(self):
        self.label_choose = tk.Label(self, text='File Path')
        self.label_choose.grid(row=0, column=0)

        self.entry_file = tk.Entry(self, textvariable=self.file_path)
        self.entry_file.grid(row=0, column=1)

        self.btn_choose = tk.Button(self, text='Choose', command=self.select)
        self.btn_choose.grid(row=0, column=2)

    # 选择文件
    def select(self):
        path = askopenfilename()
        self.file_path.set(path)

    # 输入IP地址
    def input_ipaddr(self):
        self.label_ipaddr = tk.Label(self, text='IP Address')
        self.label_ipaddr.grid(row=1, column=0)

        self.text_ipaddr = tk.Entry(self, textvariable=self.ip_addr)
        self.text_ipaddr.grid(row=1, column=1)
        self.ip_addr.set(self.text_ipaddr.get())

    # 启动
    def start(self):
        self.btn_all = tk.Button(self, text='StartAll', command=self.send_pkt)
        self.btn_all.grid(row=2, column=0)

        self.btn_udp = tk.Button(self, text='Send UDP Pkt', command=lambda: self.thread_it(self.udp_send_file))
        self.btn_udp.grid(row=2, column=1)

        self.btn_tcp = tk.Button(self, text='Send TCP Pkt', command=lambda: self.thread_it(self.tcp_send_file))
        self.btn_tcp.grid(row=2, column=2)

    def send_pkt(self):
        threading.Thread(target=self.tcp_send_file).start()
        time.sleep(1)
        threading.Thread(target=self.udp_send_file).start()

    def thread_it(self, func):
        t = threading.Thread(target=func)
        t.setDaemon(True)
        t.start()
        # 阻塞
        # t.join()

    def tcp_send_file(self):
        # 服务器IP和端口
        server_name = self.ip_addr.get()
        filepath = self.file_path.get()
        server_port = 10241

        client_socket = socket(AF_INET, SOCK_STREAM)
        client_socket.connect((server_name, server_port))

        # 获取文件大小
        file_size = os.path.getsize(filepath)
        # 告知文件大小
        print('The size of file {s} Mb'.format(s=file_size / (1024 * 1024)))
        client_socket.send(str(file_size).encode())
        time.sleep(0.3)
        BATCH_SIZE = 1024
        count = 0
        # 开始发送文件
        print('TCP: Begin send...')
        with open(filepath, 'rb') as f:
            data = f.read(BATCH_SIZE)
            while data:
                client_socket.send(data)
                data = f.read(BATCH_SIZE)
                count += 1
                if not count % 1024:
                    print('TCP: Already send {s} Mb'.format(s=count * BATCH_SIZE / (1024 * 1024.0)))

            # flag = True
            # # 处理边界问题
            # while flag:
            #     if file_size > BATCH_SIZE:
            #         data = f.read(BATCH_SIZE)
            #     else:
            #         data = f.read(file_size)
            #         flag = False
            #     #try:
            #     client_socket.send(data)
            #     #except:
            #     #    pass
            #     file_size -= BATCH_SIZE
            #
            #     count += 1
            #     if not count % 1024:
            #         print('TCP: Already send {s} Mb'.format(s=count * BATCH_SIZE / (1024 * 1024.0)))
        print('TCP: Send All Successfully!')
        client_socket.close()

    def udp_send_file(self):
        # 服务器IP地址和端口
        server_name = self.ip_addr.get()
        file_path = self.file_path.get()

        server_port = 10240
        BATCH_SIZE = 1024
        client_socket = socket(AF_INET, SOCK_DGRAM)

        # 获取文件大小

        file_size = os.path.getsize(file_path)
        # 告知服务器文件大小
        #client_socket.sendto(str(file_size).encode(), (server_name, server_port))
        batch_size = BATCH_SIZE

        # 循环发送文件
        with open(file_path, 'rb') as f:
            flag = True
            count = 0
            while flag:
                # 处理边界问题 172.20.99.244
                if file_size > batch_size:
                    data = f.read(batch_size)
                else:
                    data = f.read(file_size)
                    flag = False
                client_socket.sendto(data, (server_name, server_port))
                file_size -= batch_size

                # 计数打印
                count += 1
                if not count % 1024:
                    print('UDP: Already sent {s} Mb'.format(s=(count * BATCH_SIZE / (1024 * 1024.0))))
        client_socket.close()
        print('UDP: Send All Successfully!')

if __name__ == '__main__':
    root = tk.Tk()
    root.resizable(width=False, height=False)
    root.geometry('%dx%d+%d+%d' % (300, 100, 600, 300))
    app = Application(master=root)
    app.mainloop()
