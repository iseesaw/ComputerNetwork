# -*- coding: utf-8 -*-
import pyshark
import time
import threading
from dateutil.parser import parse
import math
import matplotlib.pyplot as plt
import numpy as np

class Monitor:
    def __init__(self):

        self.xtime = [0]
        self.tcps = [0]
        self.udps = [0]

        self.capture()

    def capture(self):
        # 以太网接口 '4'
        # WIFI接口 '1'
        cap = pyshark.LiveCapture(interface='1')
        cap.sniff(timeout=1)

        start = time.time()
        #begin = cap[0].sniff_time

        udp = 0
        tcp = 0
        plt.figure('Vs')
        plt.title('UDP Vs TCP')
        plt.ion()
        plt.show()

        for c in cap:
            if hasattr(c, 'udp'):
                if int(c.udp.dstport) == 10240:
                    udp += int(c.udp.length)
            elif hasattr(c, 'tcp'):
                if int(c.tcp.dstport) == 10241:
                    tcp += int(c.tcp.len)

            if time.time() - start > len(self.xtime):
                self.xtime.append(self.xtime[-1] + 1)
                # B -> KB
                self.udps.append(udp/1024.0)
                self.tcps.append(tcp/1024.0)
                udp = 0
                tcp = 0
                # print(self.xtime, self.udps, self.tcps)

                plt.clf()
                l1, = plt.plot(self.xtime, self.udps, color='r')
                l2, = plt.plot(self.xtime, self.tcps, color='b')
                plt.ylabel('KB/s')
                plt.xlabel('time/sec')
                plt.legend(handles=[l1, l2], labels=['UDP', 'TCP'], loc='best')
                # 必须有pause, 否则会未响应
                plt.pause(0.01)

if __name__ == '__main__':
    Monitor()
